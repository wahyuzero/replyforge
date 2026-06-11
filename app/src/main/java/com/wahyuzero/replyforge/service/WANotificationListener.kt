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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.wahyuzero.replyforge.ReplyForgeApp
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.prefs.AppPrefs
import com.wahyuzero.replyforge.engine.AutoReplyEngine
import com.wahyuzero.replyforge.network.AiService
import com.wahyuzero.replyforge.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WANotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WANotificationListener"
        private const val CHANNEL_ID = "replyforge_service"
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        private const val PACKAGE_WHATSAPP = "com.whatsapp"
        private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

        private const val KEY_TEXT_REPLY = "key_text_reply"
        private const val EXTRA_CONVERSATION = "android.intent.extra.TEXT"
    }

    private val waPackages = setOf(PACKAGE_WHATSAPP, PACKAGE_WHATSAPP_BUSINESS)

    private lateinit var autoReplyEngine: AutoReplyEngine
    private lateinit var appPrefs: AppPrefs
    private lateinit var aiService: AiService
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val processedNotifications = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        val app = application as ReplyForgeApp
        val db = AppDatabase.getInstance(this)
        appPrefs = app.appPrefs
        aiService = AiService(db.conversationDao(), db.aiUsageDao())
        autoReplyEngine = AutoReplyEngine(db.ruleDao(), db.historyDao(), appPrefs, db.holidayDao(), db.rateLimitDao(), db.aiProviderDao(), aiService)
        createNotificationChannel()
        startForeground()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
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

        if (title.isBlank() || text.isBlank()) return

        val notificationKey = sbn.key
        if (notificationKey in processedNotifications) return
        processedNotifications.add(notificationKey)

        if (processedNotifications.size > 500) {
            val iter = processedNotifications.iterator()
            repeat(250) { iter.next(); iter.remove() }
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

        if (sender.isBlank()) return
        if (sender.equals("WhatsApp", ignoreCase = true)) return
        if (sender.equals("You", ignoreCase = true)) return

        Log.d(TAG, "WA message from $sender: $text (group=$isGroup)")

        serviceScope.launch {
            try {
                val result = autoReplyEngine.processIncomingMessage(
                    sender = sender,
                    text = text,
                    isGroup = isGroup,
                    groupName = groupName
                )

                if (result != null) {
                    Log.d(TAG, "Matched rule, sending reply in ${result.delayMs}ms: ${result.replyText}")
                    handler.postDelayed({
                        sendReply(sbn, result.replyText)
                    }, result.delayMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
            }
        }
    }

    private fun sendReply(sbn: StatusBarNotification, replyText: String) {
        val notification = sbn.notification ?: return
        val actions = notification.actions ?: return

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
            Log.w(TAG, "No reply action found in notification")
            return
        }

        try {
            val intent = Intent()
            val bundle = android.os.Bundle()
            for (input in replyAction.remoteInputs ?: emptyArray()) {
                bundle.putCharSequence(input.resultKey, replyText)
            }
            intent.putExtras(bundle)
            replyAction.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "Reply sent successfully: $replyText")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply", e)
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
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
