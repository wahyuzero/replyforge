package com.wahyuzero.replyforge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wahyuzero.replyforge.ReplyForgeApp
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.prefs.AppPrefs
import com.wahyuzero.replyforge.engine.AutoReplyEngine
import com.wahyuzero.replyforge.engine.ReplyResult
import com.wahyuzero.replyforge.network.AiService
import com.wahyuzero.replyforge.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * WhatsApp Notification Listener — based on Watomatic/AutoResponder approach.
 *
 * Reply strategies (in order):
 * 1. Direct notification action with RemoteInput (standard)
 * 2. WearableExtender hidden actions (Android Wear reply)
 * 3. Car extensions (android.car.EXTENSIONS bundle)
 * 4. Delayed re-check of active notifications (WA sends dupes, some with actions)
 * 5. Search ALL active WA notifications for any reply action
 *
 * Key: Uses RemoteInput.addResultsToIntent() — the proper Android API for
 * notification replies, same mechanism Android Wear uses.
 */
class WANotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RF_DEBUG"
        private const val CHANNEL_ID = "replyforge_service"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val PACKAGE_WHATSAPP = "com.whatsapp"
        private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val SENDER_WHATSAPP = "WhatsApp"
        const val MAX_PROCESSED = 500
        const val EVICT_THRESHOLD = 250
        const val RECHECK_DELAY_MS = 500L
        private val SELF_NAMES = setOf("You", "Anda", "you", "anda")
    }

    private val waPackages = setOf(PACKAGE_WHATSAPP, PACKAGE_WHATSAPP_BUSINESS)
    private lateinit var autoReplyEngine: AutoReplyEngine
    private lateinit var appPrefs: AppPrefs
    private lateinit var aiService: AiService
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val processedNotifications = java.util.LinkedHashMap<String, Boolean>(512, 0.75f, true)
    private var isServiceDestroyed = false

    // ── Lifecycle ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== SERVICE onCreate ===")
        try {
            val app = application as? ReplyForgeApp ?: return
            val db = AppDatabase.getInstance(this)
            appPrefs = app.appPrefs
            aiService = AiService(db.conversationDao(), db.aiUsageDao())
            autoReplyEngine = AutoReplyEngine(
                db.ruleDao(), db.historyDao(), appPrefs,
                db.holidayDao(), db.rateLimitDao(), db.aiProviderDao(), aiService
            )
            createNotificationChannel()
            startForeground()
            Log.d(TAG, "=== SERVICE onCreate SUCCESS ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== SERVICE onCreate FAILED ===", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "=== LISTENER CONNECTED ===")
        val activeCount = activeNotifications?.size ?: 0
        Log.d(TAG, "Active notifications: $activeCount")
        serviceScope.launch {
            val enabled = appPrefs.autoReplyEnabled.first()
            Log.d(TAG, "Auto-reply enabled in prefs: $enabled")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "=== LISTENER DISCONNECTED ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== SERVICE DESTROYED ===")
        isServiceDestroyed = true
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        processedNotifications.clear()
    }

    // ── Notification Handler ───────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (isServiceDestroyed) return
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.packageName !in waPackages) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        Log.d(TAG, "=== WA NOTIF: title='$title' text='${text.take(60)}' id=${sbn.id} ===")

        if (title.isBlank() || text.isBlank()) return
        // Dedup by notification key
        val notifKey = sbn.key ?: return
        if (processedNotifications.containsKey(notifKey)) {
            Log.d(TAG, "Already processed: $notifKey")
            return
        }
        processedNotifications[notifKey] = true
        if (processedNotifications.size > MAX_PROCESSED) {
            val iter = processedNotifications.keys.iterator()
            repeat(EVICT_THRESHOLD) { iter.next(); iter.remove() }
        }

        // Parse sender
        val isGroup = title.contains(":") || extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val sender: String
        val groupName: String?
        if (isGroup) {
            val ci = title.indexOf(":")
            sender = title.substring(ci + 1).trim()
            groupName = title.substring(0, ci).trim()
        } else {
            sender = title.trim()
            groupName = null
        }

        if (sender.isBlank() || sender in SELF_NAMES || sender.equals(SENDER_WHATSAPP, ignoreCase = true)) {
            Log.d(TAG, "Skip: sender='$sender'")
            return
        }

        Log.d(TAG, ">>> PROCESSING: from=$sender msg='$text' isGroup=$isGroup <<<")
        dumpAllActions(notification)

        if (isServiceDestroyed) return
        serviceScope.launch {
            if (isServiceDestroyed) return@launch
            try {
                val result = autoReplyEngine.processIncomingMessage(sender, text, isGroup, groupName)
                if (result != null) {
                    Log.d(TAG, "=== MATCH: rule='${result.matchedRule.name}' delay=${result.delayMs}ms ===")
                    if (!isServiceDestroyed) {
                        handler.postDelayed({ sendReply(sbn, result) }, result.delayMs)
                    }
                } else {
                    Log.d(TAG, "No matching rule for '$text'")
                    val db = AppDatabase.getInstance(this@WANotificationListener)
                    val rules = db.ruleDao().getEnabledRules().first()
                    Log.d(TAG, "Enabled rules: ${rules.size}")
                    rules.forEach { Log.d(TAG, "  Rule: '${it.name}' pattern='${it.pattern}'") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message: ${e.message}", e)
            }
        }
    }

    // ── Debug Dump ─────────────────────────────────────────────

    private fun dumpAllActions(notification: Notification) {
        // Standard actions
        val stdActions = notification.actions
        Log.d(TAG, "  Std actions: ${stdActions?.size ?: 0}")
        stdActions?.forEachIndexed { i, a ->
            val ri = a.remoteInputs
            Log.d(TAG, "    Std[$i]: '${a.title}' remoteInputs=${ri?.size ?: 0}")
        }

        // WearableExtender
        val wearActions = NotificationCompat.WearableExtender(notification).actions
        Log.d(TAG, "  Wear actions: ${wearActions.size}")
        wearActions.forEachIndexed { i, a ->
            val ri = a.remoteInputs
            Log.d(TAG, "    Wear[$i]: '${a.title}' remoteInputs=${ri?.size ?: 0}")
        }

        // Compat action count
        val compatCount = NotificationCompat.getActionCount(notification)
        Log.d(TAG, "  Compat action count: $compatCount")

        // Car extensions
        try {
            val carExt = notification.extras?.getBundle("android.car.EXTENSIONS")
            if (carExt != null) {
                Log.d(TAG, "  Car extensions found: ${carExt.keySet()}")
                val conv = carExt.getBundle("car_conversation")
                if (conv != null) {
                    Log.d(TAG, "  Car conversation keys: ${conv.keySet()}")
                }
            }
        } catch (_: Exception) {}
    }

    // ── Reply Action Extraction ────────────────────────────────

    /**
     * Holds everything needed to send a notification reply.
     * Based on Watomatic's NotificationWear pattern.
     */
    private data class ReplyAction(
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>,
        val source: String
    )

    /**
     * Extract reply action from a single notification.
     * Tries: standard → wearable → compat → car extensions.
     *
     * Key difference from previous attempts:
     * - Checks allowFreeFormInput (must be true for text reply)
     * - Stores actual RemoteInput objects (needed for addResultsToIntent)
     * - Checks car extensions bundle
     */
    private fun extractReplyAction(notification: Notification): ReplyAction? {
        // ── Strategy 1: Standard notification.actions ──
        notification.actions?.forEach { action ->
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                for (ri in remoteInputs) {
                    if (ri.allowFreeFormInput) {
                        Log.d(TAG, "  → Reply via STANDARD: '${action.title}' key='${ri.resultKey}'")
                        return ReplyAction(action.actionIntent, remoteInputs, "standard")
                    }
                }
            }
        }

        // ── Strategy 2: WearableExtender hidden actions ──
        val wearActions = NotificationCompat.WearableExtender(notification).actions
        for (wAction in wearActions) {
            val compatRemoteInputs = wAction.remoteInputs
            if (compatRemoteInputs != null && compatRemoteInputs.isNotEmpty()) {
                for (ri in compatRemoteInputs) {
                    if (ri.allowFreeFormInput) {
                        Log.d(TAG, "  → Reply via WEARABLE: '${wAction.title}' key='${ri.resultKey}'")
                        val pi = wAction.actionIntent ?: continue
                        // Build framework RemoteInput from compat RemoteInput
                        val frameworkInputs = compatRemoteInputs.map { cri ->
                            RemoteInput.Builder(cri.resultKey)
                                .setLabel(cri.label)
                                .setChoices(cri.choices)
                                .setAllowFreeFormInput(cri.allowFreeFormInput)
                                .build()
                        }.toTypedArray()
                        return ReplyAction(pi, frameworkInputs, "wearable")
                    }
                }
            }
        }

        // ── Strategy 3: NotificationCompat.getAction (unwraps all) ──
        val actionCount = NotificationCompat.getActionCount(notification)
        for (i in 0 until actionCount) {
            val compatAction = NotificationCompat.getAction(notification, i) ?: continue
            val compatRemoteInputs = compatAction.remoteInputs
            if (compatRemoteInputs != null && compatRemoteInputs.isNotEmpty()) {
                for (ri in compatRemoteInputs) {
                    if (ri.allowFreeFormInput) {
                        Log.d(TAG, "  → Reply via COMPAT[$i]: '${compatAction.title}' key='${ri.resultKey}'")
                        val pi = compatAction.actionIntent ?: continue
                        val frameworkInputs = compatRemoteInputs.map { cri ->
                            RemoteInput.Builder(cri.resultKey)
                                .setLabel(cri.label)
                                .setChoices(cri.choices)
                                .setAllowFreeFormInput(cri.allowFreeFormInput)
                                .build()
                        }.toTypedArray()
                        return ReplyAction(pi, frameworkInputs, "compat")
                    }
                }
            }
        }

        // ── Strategy 4: Car extensions ──
        try {
            val carExt = notification.extras?.getBundle("android.car.EXTENSIONS")
            val conv = carExt?.getBundle("car_conversation")
            val replyPi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                conv?.getParcelable("on_reply", PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                conv?.getParcelable<PendingIntent>("on_reply")
            }
            if (replyPi != null) {
                // Car reply uses a specific result key
                val replyKey = conv?.getString("reply_key") ?: "reply"
                val ri = RemoteInput.Builder(replyKey)
                    .setAllowFreeFormInput(true)
                    .build()
                Log.d(TAG, "  → Reply via CAR_EXTENSIONS")
                return ReplyAction(replyPi, arrayOf(ri), "car")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Car extension extraction failed", e)
        }

        return null
    }

    // ── Send Reply ─────────────────────────────────────────────

    /**
     * Attempt to send reply using multiple strategies.
     * 1. Extract from current notification
     * 2. Delayed re-check (WA sends dupes with actions)
     * 3. Search all active WA notifications
     */
    private fun sendReply(sbn: StatusBarNotification, result: ReplyResult) {
        val notification = sbn.notification ?: return

        // Strategy 1: Direct from current notification
        val direct = extractReplyAction(notification)
        if (direct != null) {
            Log.d(TAG, "Using reply from current notif (${direct.source})")
            if (executeReply(direct, result)) {
                logReply(result)
                return
            }
        }

        // Strategy 2: Search all active WA notifications right now
        Log.w(TAG, "No reply action in current notif, searching active...")
        if (searchActiveAndSend(result)) return

        // Strategy 3: Delayed re-check (WA sends duplicate notifications,
        // sometimes the second one has reply actions)
        Log.d(TAG, "Scheduling delayed re-check in ${RECHECK_DELAY_MS}ms...")
        if (!isServiceDestroyed) {
            handler.postDelayed({
                if (isServiceDestroyed) return@postDelayed
                Log.d(TAG, "=== DELAYED RE-CHECK ===")
                // Re-check current notification first
                val recheck = extractReplyAction(notification)
                if (recheck != null) {
                    Log.d(TAG, "Delayed: found reply in current notif (${recheck.source})")
                    if (executeReply(recheck, result)) {
                        logReply(result)
                        return@postDelayed
                    }
                }
                // Then search all active
                if (!searchActiveAndSend(result)) {
                    Log.e(TAG, "=== ALL STRATEGIES FAILED ===")
                    Log.e(TAG, "Tip: Check WhatsApp notification settings -> 'Conversation notifications' ON")
                    Log.e(TAG, "Tip: Lock screen -> 'Show all notification content'")
                    Log.e(TAG, "Tip: MIUI -> Settings -> Notifications -> Full Screen Notification -> enable WA")
                }
            }, RECHECK_DELAY_MS)
        }
    }

    /** Search all active WA notifications for a reply action.
     * Returns true if reply was sent. */
    private fun searchActiveAndSend(result: ReplyResult): Boolean {
         val activeSbns = activeNotifications ?: return false

         for (activeSbn in activeSbns) {
             if (activeSbn.packageName !in waPackages) continue
             // Try ALL notifications including current (it might have changed)

             val activeNotif = activeSbn.notification ?: continue
             val found = extractReplyAction(activeNotif)
             if (found != null) {
                 Log.d(TAG, "  -> Found reply in active notif id=${activeSbn.id} (${found.source})")
                 if (executeReply(found, result)) {
                     logReply(result)
                     return true
                 }
             }
         }
         return false
     }

    /**
     * Execute a notification reply using RemoteInput.addResultsToIntent().
     *
     * This is the CRITICAL function — same API Android Wear uses to reply.
     * Previous attempts failed because they used Intent.putExtras(bundle)
     * instead of RemoteInput.addResultsToIntent().
     *
     * The proper way (from Watomatic source):
     * 1. Create Bundle with reply text keyed to RemoteInput.resultKey
     * 2. Use RemoteInput.addResultsToIntent() to attach results
     * 3. Send via PendingIntent.send()
     */
    private fun executeReply(replyAction: ReplyAction, result: ReplyResult): Boolean {
        return try {
            // Find the best RemoteInput (prefer allowFreeFormInput)
            val bestRemoteInput = replyAction.remoteInputs.firstOrNull { it.allowFreeFormInput }
                ?: replyAction.remoteInputs.firstOrNull()
                ?: return false

            // Build the reply bundle
            val replyBundle = Bundle()
            replyBundle.putCharSequence(bestRemoteInput.resultKey, result.replyText)

            // Create intent and add RemoteInput results
            // THIS IS THE KEY LINE — different from our previous approach
            val replyIntent = Intent()
            replyIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(replyAction.remoteInputs, replyIntent, replyBundle)

            // Send via PendingIntent
            replyAction.pendingIntent.send(applicationContext, 0, replyIntent)

            Log.d(TAG, "=== REPLY SENT: '${result.replyText}' via ${replyAction.source} ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reply execution failed (${replyAction.source})", e)
            false
        }
    }

    // ── Logging ────────────────────────────────────────────────

    private fun logReply(result: ReplyResult) {
        serviceScope.launch {
            autoReplyEngine.logReply(
                result.matchedRule.id, result.sender, result.originalText,
                result.replyText, result.isGroup, result.groupName, result.processTimeMs
            )
            Log.d(TAG, "Reply logged to history")
        }
    }

    // ── Foreground Service ─────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        Log.d(TAG, "=== FOREGROUND SERVICE STARTED ===")
    }
}
