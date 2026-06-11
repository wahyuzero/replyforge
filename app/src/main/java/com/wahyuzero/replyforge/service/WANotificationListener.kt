package com.wahyuzero.replyforge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
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
        const val SENDER_YOU = "You"
        const val SENDER_YOU_ID = "Anda"  // Indonesian localization of "You"
        const val MAX_PROCESSED_NOTIFICATIONS = 500
        const val EVICT_THRESHOLD = 250
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

        val packageName = sbn.packageName
        if (packageName !in waPackages) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        Log.d(TAG, "=== WA NOTIFICATION: pkg=$packageName ===")
        Log.d(TAG, "WA notification: title='$title' text='$text'")

        if (title.isBlank() || text.isBlank()) {
            Log.d(TAG, "WA notification: blank title or text, skipping")
            return
        }

        val notificationKey = sbn.key ?: return

        if (processedNotifications.containsKey(notificationKey)) {
            Log.d(TAG, "WA notification: already processed key=$notificationKey")
            return
        }
        processedNotifications[notificationKey] = true

        if (processedNotifications.size > MAX_PROCESSED_NOTIFICATIONS) {
            val iter = processedNotifications.keys.iterator()
            repeat(EVICT_THRESHOLD) { iter.next(); iter.remove() }
        }

        val isGroup = title.contains(":") || extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val sender: String
        val groupName: String?

        if (isGroup) {
            val colonIndex = title.indexOf(":")
            sender = title.substring(colonIndex + 1).trim()
            groupName = title.substring(0, colonIndex).trim()
        } else {
            sender = title.trim()
            groupName = null
        }

        Log.d(TAG, "Parsed: sender='$sender' isGroup=$isGroup group='$groupName'")

        if (sender.isBlank()) return
        if (sender.equals(SENDER_WHATSAPP, ignoreCase = true)) {
            Log.d(TAG, "Sender is 'WhatsApp' system message, skipping")
            return
        }
        // Skip own messages - handle both "You" and localized versions
        if (sender.equals(SENDER_YOU, ignoreCase = true) ||
            sender.equals(SENDER_YOU_ID, ignoreCase = true)) {
            Log.d(TAG, "Sender is own message ('$sender'), skipping")
            return
        }

        Log.d(TAG, ">>> PROCESSING: from=$sender msg='$text' <<<")

        // Check for reply actions in THIS notification
        val hasReplyAction = hasReplyAction(notification)
        Log.d(TAG, "This notification has reply action: $hasReplyAction")

        serviceScope.launch {
            try {
                val result = autoReplyEngine.processIncomingMessage(
                    sender = sender,
                    text = text,
                    isGroup = isGroup,
                    groupName = groupName
                )

                if (result != null) {
                    Log.d(TAG, "=== MATCH FOUND! Rule: ${result.matchedRule.name}, delay: ${result.delayMs}ms ===")
                    Log.d(TAG, "Reply text: '${result.replyText}'")
                    handler.postDelayed({
                        sendReply(sbn, result)
                    }, result.delayMs)
                } else {
                    Log.d(TAG, "No matching rule for: '$text'")
                    val db = AppDatabase.getInstance(this@WANotificationListener)
                    val rules = db.ruleDao().getEnabledRules().first()
                    Log.d(TAG, "Enabled rules: ${rules.size}")
                    rules.forEach { rule ->
                        Log.d(TAG, "  Rule: '${rule.name}' pattern='${rule.pattern}' matchType=${rule.matchType}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
            }
        }
    }

    private fun hasReplyAction(notification: Notification): Boolean {
        val actions = notification.actions ?: return false
        return actions.any { it.remoteInputs?.isNotEmpty() == true }
    }

    /**
     * Find a reply action from ANY active WhatsApp notification.
     * WhatsApp often posts multiple notifications - some have reply actions, some don't.
     * We search all active WA notifications to find one with a usable reply action.
     */
    private fun findReplyActionFromActiveNotifications(): Pair<Notification.Action, android.app.RemoteInput>? {
        val activeSbns = activeNotifications ?: return null
        Log.d(TAG, "Searching ${activeSbns.size} active notifications for reply action...")

        for (activeSbn in activeSbns) {
            if (activeSbn.packageName !in waPackages) continue
            val activeNotif = activeSbn.notification ?: continue
            val actions = activeNotif.actions ?: continue

            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    val actionName = action.title?.toString() ?: "?"
                    Log.d(TAG, "Found reply action in active notification: '$actionName' (key=${activeSbn.key})")
                    return Pair(action, remoteInputs[0])
                }
            }
        }

        Log.w(TAG, "No reply action found in any active WA notification!")
        return null
    }

    /**
     * Try to find a reply action specific to the sender by checking notification extras.
     */
    private fun findReplyActionForSender(sender: String): Pair<Notification.Action, android.app.RemoteInput>? {
        val activeSbns = activeNotifications ?: return null

        // First try: find notification matching the sender
        for (activeSbn in activeSbns) {
            if (activeSbn.packageName !in waPackages) continue
            val activeNotif = activeSbn.notification ?: continue
            val extras = activeNotif.extras ?: continue
            val notifTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: continue

            // Check if this notification is from the same sender
            val matchSender = notifTitle.equals(sender, ignoreCase = true) ||
                (notifTitle.contains(":") && notifTitle.substringAfter(":").trim().equals(sender, ignoreCase = true))

            if (matchSender) {
                val actions = activeNotif.actions ?: continue
                for (action in actions) {
                    val remoteInputs = action.remoteInputs
                    if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                        Log.d(TAG, "Found sender-specific reply action for '$sender' in key=${activeSbn.key}")
                        return Pair(action, remoteInputs[0])
                    }
                }
            }
        }

        // Second try: find any WA notification with a reply action (for grouped notifications)
        for (activeSbn in activeSbns) {
            if (activeSbn.packageName !in waPackages) continue
            val activeNotif = activeSbn.notification ?: continue
            val extras = activeNotif.extras ?: continue
            val notifTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: continue

            // Skip self notifications
            if (notifTitle.equals(SENDER_YOU, ignoreCase = true) ||
                notifTitle.equals(SENDER_YOU_ID, ignoreCase = true)) continue

            val actions = activeNotif.actions ?: continue
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    Log.d(TAG, "Found fallback reply action from '$notifTitle' in key=${activeSbn.key}")
                    return Pair(action, remoteInputs[0])
                }
            }
        }

        // Third try: even self notifications (last resort)
        return findReplyActionFromActiveNotifications()
    }

    private fun sendReply(sbn: StatusBarNotification, result: ReplyResult) {
        val notification = sbn.notification

        // Strategy 1: Try reply action from THIS notification
        var replyAction: Notification.Action? = null
        var remoteInput: android.app.RemoteInput? = null

        val actions = notification?.actions
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    replyAction = action
                    remoteInput = remoteInputs[0]
                    break
                }
            }
        }

        // Strategy 2: Search active notifications for a reply action
        if (replyAction == null) {
            Log.w(TAG, "No reply action in current notification, searching active notifications...")
            val found = findReplyActionForSender(result.sender)
            if (found != null) {
                replyAction = found.first
                remoteInput = found.second
                Log.d(TAG, "Using reply action from another active notification")
            }
        }

        // Strategy 3: Last resort - try NotificationCompat to unwrap wearable actions
        if (replyAction == null) {
            Log.w(TAG, "Trying NotificationCompat wearable actions...")
            if (notification != null) {
                val compatActions = NotificationCompat.getAction(notification, 0)
                if (compatActions != null) {
                    val compatRemoteInputs = compatActions.remoteInputs
                    if (compatRemoteInputs != null && compatRemoteInputs.isNotEmpty()) {
                        Log.d(TAG, "Found action via NotificationCompat!")
                        // Use the compat action's action intent
                        val bundle = android.os.Bundle()
                        for (input in compatRemoteInputs) {
                            bundle.putCharSequence(input.resultKey, result.replyText)
                        }
                        try {
                            val intent = Intent()
                            intent.putExtras(bundle)
                            compatActions.actionIntent?.send(applicationContext, 0, intent)
                            Log.d(TAG, "=== REPLY SENT SUCCESS (via NotificationCompat): '${result.replyText}' ===")
                            logReply(result)
                            return
                        } catch (e: Exception) {
                            Log.e(TAG, "NotificationCompat reply failed", e)
                        }
                    }
                }
            }
        }

        if (replyAction == null || remoteInput == null) {
            Log.e(TAG, "=== ALL REPLY STRATEGIES FAILED ===")
            Log.e(TAG, "Cannot send reply to '$result.sender'. WhatsApp notification has no reply action.")
            Log.e(TAG, "Tips: Check WhatsApp notification settings → make sure 'Show on lock screen' is enabled")
            return
        }

        // Send the reply using the found action
        try {
            val intent = Intent()
            val bundle = android.os.Bundle()
            for (input in replyAction.remoteInputs ?: emptyArray()) {
                bundle.putCharSequence(input.resultKey, result.replyText)
            }
            intent.putExtras(bundle)
            replyAction.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "=== REPLY SENT SUCCESS: '${result.replyText}' ===")
            logReply(result)
        } catch (e: Exception) {
            Log.e(TAG, "=== REPLY SEND FAILED ===", e)
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
