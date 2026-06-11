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
        
        // Log current status
        serviceScope.launch {
            val enabled = appPrefs.autoReplyEnabled.first()
            Log.d(TAG, "Auto-reply enabled in prefs: $enabled")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) {
            Log.d(TAG, "Notification posted: NULL")
            return
        }

        val packageName = sbn.packageName
        
        // Log ALL notifications for debugging
        if (packageName in waPackages) {
            Log.d(TAG, "=== WA NOTIFICATION: pkg=$packageName ===")
        } else {
            // Skip non-WA silently but log first few
            return
        }

        val notification = sbn.notification ?: run {
            Log.d(TAG, "WA notification: NULL notification object")
            return
        }
        val extras = notification.extras ?: run {
            Log.d(TAG, "WA notification: NULL extras")
            return
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: run {
            Log.d(TAG, "WA notification: NULL title. Extra keys: ${extras.keySet()}")
            return
        }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: run {
            Log.d(TAG, "WA notification: NULL text. Title=$title")
            return
        }

        Log.d(TAG, "WA notification: title='$title' text='$text'")

        if (title.isBlank() || text.isBlank()) {
            Log.d(TAG, "WA notification: blank title or text, skipping")
            return
        }

        val notificationKey = sbn.key ?: run {
            Log.d(TAG, "WA notification: NULL key")
            return
        }
        
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

        if (sender.isBlank()) {
            Log.d(TAG, "Sender blank, skipping")
            return
        }
        if (sender.equals(SENDER_WHATSAPP, ignoreCase = true)) {
            Log.d(TAG, "Sender is 'WhatsApp' system message, skipping")
            return
        }
        if (sender.equals(SENDER_YOU, ignoreCase = true)) {
            Log.d(TAG, "Sender is 'You' (own message), skipping")
            return
        }

        Log.d(TAG, ">>> PROCESSING: from=$sender msg='$text' <<<")

        // Log available actions
        val actions = notification.actions
        Log.d(TAG, "Notification has ${actions?.size ?: 0} actions")
        actions?.forEachIndexed { i, action ->
            val hasRemoteInput = action.remoteInputs?.isNotEmpty() == true
            Log.d(TAG, "  Action[$i]: name='${action.title}' remoteInput=$hasRemoteInput")
        }

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
                    Log.d(TAG, "No matching rule found for: '$text'")
                    
                    // Debug: check why no match
                    val db = AppDatabase.getInstance(this@WANotificationListener)
                    val rules = db.ruleDao().getEnabledRules().first()
                    Log.d(TAG, "Enabled rules count: ${rules.size}")
                    rules.forEach { rule ->
                        Log.d(TAG, "  Rule: id=${rule.id} name='${rule.name}' pattern='${rule.pattern}' matchType=${rule.matchType} enabled=${rule.enabled}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
            }
        }
    }

    private fun sendReply(sbn: StatusBarNotification, result: ReplyResult) {
        val notification = sbn.notification ?: return
        val actions = notification.actions ?: run {
            Log.e(TAG, "SEND REPLY: No actions in notification!")
            return
        }

        var replyAction: Notification.Action? = null
        var remoteInput: android.app.RemoteInput? = null

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                replyAction = action
                remoteInput = remoteInputs[0]
                break
            }
        }

        if (replyAction == null || remoteInput == null) {
            Log.e(TAG, "SEND REPLY: No reply action/remoteInput found in notification!")
            Log.e(TAG, "This usually means WhatsApp notification doesn't support direct reply")
            Log.e(TAG, "Make sure WhatsApp notifications are set to show on lock screen")
            return
        }

        try {
            val intent = Intent()
            val bundle = android.os.Bundle()
            for (input in replyAction.remoteInputs ?: emptyArray()) {
                bundle.putCharSequence(input.resultKey, result.replyText)
            }
            intent.putExtras(bundle)
            replyAction.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "=== REPLY SENT SUCCESS: '${result.replyText}' ===")
            
            serviceScope.launch {
                autoReplyEngine.logReply(
                    result.matchedRule.id, result.sender, result.originalText,
                    result.replyText, result.isGroup, result.groupName, result.processTimeMs
                )
                Log.d(TAG, "Reply logged to history")
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== REPLY SEND FAILED ===", e)
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
