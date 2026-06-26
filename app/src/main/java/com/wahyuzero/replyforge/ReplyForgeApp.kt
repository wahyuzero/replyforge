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
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())
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

    private inner class CrashHandler : Thread.UncaughtExceptionHandler {
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            Log.e("ReplyForge", "FATAL CRASH", throwable)

            // Write crash log to file
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                openFileOutput("crash.log", MODE_PRIVATE).use { fos ->
                    fos.write(sw.toString().toByteArray())
                }
            } catch (e: Exception) {
                Log.e("ReplyForge", "Failed to write crash log", e)
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CHANNEL_ID = "replyforge_service"
    }
}
