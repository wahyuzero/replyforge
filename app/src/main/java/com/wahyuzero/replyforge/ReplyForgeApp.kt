package com.wahyuzero.replyforge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.wahyuzero.replyforge.data.prefs.AppPrefs
import com.wahyuzero.replyforge.worker.ConversationCleanupWorker
import java.util.concurrent.TimeUnit

class ReplyForgeApp : Application() {

    val appPrefs: AppPrefs by lazy { AppPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        createNotificationChannel()
        scheduleConversationCleanup()
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun scheduleConversationCleanup() {
        val request = PeriodicWorkRequest.Builder(
            ConversationCleanupWorker::class.java,
            24, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                ConversationCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    companion object {
        const val CHANNEL_ID = "replyforge_service"
    }
}
