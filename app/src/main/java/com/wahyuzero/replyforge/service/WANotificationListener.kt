package com.wahyuzero.replyforge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

class WANotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RF_DEBUG"
        private const val CHANNEL_ID = "replyforge_service"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val PACKAGE_WHATSAPP = "com.whatsapp"
        private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val SENDER_WHATSAPP = "WhatsApp"
        const val MAX_PROCESSED_NOTIFICATIONS = 500
        const val EVICT_THRESHOLD = 250
        private val SELF_NAMES = setOf("You", "Anda", "you", "anda")
    }

    private val waPackages = setOf(PACKAGE_WHATSAPP, PACKAGE_WHATSAPP_BUSINESS)
    private lateinit var autoReplyEngine: AutoReplyEngine
    private lateinit var appPrefs: AppPrefs
    private lateinit var aiService: AiService
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val processedNotifications = java.util.LinkedHashMap<String, Boolean>(512, 0.75f, true)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== SERVICE onCreate ===")
        try {
            val app = application as ReplyForgeApp
            val db = AppDatabase.getInstance(this)
            appPrefs = app.appPrefs
            aiService = AiService(db.conversationDao(), db.aiUsageDao())
            autoReplyEngine = AutoReplyEngine(db.ruleDao(), db.historyDao(), appPrefs, db.holidayDao(), db.rateLimitDao(), db.aiProviderDao(), aiService)
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
        Log.d(TAG, "Active notifications: ${activeNotifications?.size ?: 0}")
        serviceScope.launch {
            val enabled = appPrefs.autoReplyEnabled.first()
            Log.d(TAG, "Auto-reply enabled in prefs: $enabled")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.packageName !in waPackages) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        Log.d(TAG, "=== WA NOTIF: title='$title' text='${text.take(80)}' id=${sbn.id} tag=${sbn.tag} ===")

        if (title.isBlank() || text.isBlank()) return

        val notificationKey = sbn.key ?: return
        if (processedNotifications.containsKey(notificationKey)) {
            Log.d(TAG, "Already processed: $notificationKey")
            return
        }
        processedNotifications[notificationKey] = true
        if (processedNotifications.size > MAX_PROCESSED_NOTIFICATIONS) {
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

        // Dump ALL actions from every source for this notification
        dumpAllActions(notification, sbn.id)

        serviceScope.launch {
            try {
                val result = autoReplyEngine.processIncomingMessage(sender, text, isGroup, groupName)
                if (result != null) {
                    Log.d(TAG, "=== MATCH: rule='${result.matchedRule.name}' delay=${result.delayMs}ms reply='${result.replyText}' ===")
                    handler.postDelayed({ sendReply(sbn, result) }, result.delayMs)
                } else {
                    Log.d(TAG, "No matching rule for '$text'")
                    val db = AppDatabase.getInstance(this@WANotificationListener)
                    val rules = db.ruleDao().getEnabledRules().first()
                    Log.d(TAG, "Enabled rules: ${rules.size}")
                    rules.forEach { Log.d(TAG, "  Rule: '${it.name}' pattern='${it.pattern}' matchType=${it.matchType}") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
            }
        }
    }

    /**
     * Dump all possible action sources for debugging
     */
    private fun dumpAllActions(notification: Notification, notifId: Int) {
        // 1. Standard actions
        val stdActions = notification.actions
        Log.d(TAG, "  Standard actions: ${stdActions?.size ?: 0}")
        stdActions?.forEachIndexed { i, a ->
            val hasRI = a.remoteInputs?.isNotEmpty() == true
            Log.d(TAG, "    Std[$i]: name='${a.title}' remoteInput=$hasRI")
        }

        // 2. WearableExtender actions (hidden reply actions!)
        val wearableActions = NotificationCompat.WearableExtender(notification).actions
        Log.d(TAG, "  Wearable actions: ${wearableActions.size}")
        wearableActions.forEachIndexed { i, a ->
            val hasRI = a.remoteInputs?.isNotEmpty() == true
            Log.d(TAG, "    Wear[$i]: name='${a.title}' remoteInput=$hasRI")
        }

        // 3. NotificationCompat actions (unwraps all)
        val compatActionCount = try {
            var count = 0
            while (true) {
                val a = NotificationCompat.getAction(notification, count)
                if (a == null) break
                count++
            }
            count
        } catch (e: Exception) { 0 }
        Log.d(TAG, "  NotificationCompat action count: $compatActionCount")
    }

    /**
     * Find a reply action from a notification using ALL possible sources:
     * 1. Standard notification.actions
     * 2. NotificationCompat.WearableExtender actions
     * 3. NotificationCompat.getAction (unwraps all)
     */
    private data class ReplyAction(
        val actionIntent: PendingIntent,
        val resultKeys: List<String>,
        val source: String
    )

    private fun findReplyActionInNotification(notification: Notification): ReplyAction? {
        // Strategy 1: Standard actions with RemoteInput
        notification.actions?.forEach { action ->
            val ri = action.remoteInputs
            if (ri != null && ri.isNotEmpty()) {
                Log.d(TAG, "  → Found reply via standard action: '${action.title}'")
                return ReplyAction(
                    action.actionIntent,
                    ri.map { it.resultKey },
                    "standard"
                )
            }
        }

        // Strategy 2: WearableExtender actions
        val wearableActions = NotificationCompat.WearableExtender(notification).actions
        for (wAction in wearableActions) {
            val ri = wAction.remoteInputs
            if (ri != null && ri.isNotEmpty()) {
                Log.d(TAG, "  → Found reply via WearableExtender: '${wAction.title}'")
                return ReplyAction(
                    wAction.actionIntent ?: continue,
                    ri.map { it.resultKey },
                    "wearable"
                )
            }
        }

        // Strategy 3: NotificationCompat.getAction
        var idx = 0
        while (true) {
            val compatAction = NotificationCompat.getAction(notification, idx) ?: break
            val ri = compatAction.remoteInputs
            if (ri != null && ri.isNotEmpty()) {
                Log.d(TAG, "  → Found reply via NotificationCompat.getAction[$idx]: '${compatAction.title}'")
                return ReplyAction(
                    compatAction.actionIntent ?: break,
                    ri.map { it.resultKey },
                    "compat"
                )
            }
            idx++
        }

        return null
    }

    private fun sendReply(sbn: StatusBarNotification, result: ReplyResult) {
        val notification = sbn.notification ?: return

        // Strategy 1: Find reply action in THIS notification (all sources)
        val found = findReplyActionInNotification(notification)
        if (found != null) {
            Log.d(TAG, "Using reply from current notif (source=${found.source})")
            if (executeReply(found, result)) {
                logReply(result)
                return
            }
        }

        // Strategy 2: Search active WA notifications for reply action
        Log.w(TAG, "No reply in current notification, searching active notifications...")
        val activeSbns = activeNotifications ?: emptyArray()
        for (activeSbn in activeSbns) {
            if (activeSbn.packageName !in waPackages) continue
            if (activeSbn.key == sbn.key) continue

            val activeNotif = activeSbn.notification ?: continue
            val activeFound = findReplyActionInNotification(activeNotif)
            if (activeFound != null) {
                Log.d(TAG, "  → Using reply from active notif id=${activeSbn.id} (source=${activeFound.source})")
                if (executeReply(activeFound, result)) {
                    logReply(result)
                    return
                }
            }
        }

        Log.e(TAG, "=== ALL REPLY STRATEGIES FAILED ===")
        Log.e(TAG, "Cannot send reply to '${result.sender}'. No usable reply action found.")
        Log.e(TAG, "WhatsApp might need 'Show on lock screen' enabled in notification settings.")
    }

    /**
     * Execute a reply using a ReplyAction
     */
    private fun executeReply(replyAction: ReplyAction, result: ReplyResult): Boolean {
        return try {
            val bundle = android.os.Bundle()
            for (key in replyAction.resultKeys) {
                bundle.putCharSequence(key, result.replyText)
            }
            val intent = Intent()
            intent.putExtras(bundle)
            replyAction.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "=== REPLY SENT: '${result.replyText}' via ${replyAction.source} ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reply execution failed", e)
            false
        }
    }

    private fun logReply(result: ReplyResult) {
        serviceScope.launch {
            autoReplyEngine.logReply(
                result.matchedRule.id, result.sender, result.originalText,
                result.replyText, result.isGroup, result.groupName, result.processTimeMs
            )
            Log.d(TAG, "Reply logged to history")
        }
    }

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

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "=== LISTENER DISCONNECTED ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== SERVICE DESTROYED ===")
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
