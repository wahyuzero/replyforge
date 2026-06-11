package com.wahyuzero.replyforge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wahyuzero.replyforge.data.prefs.AppPrefs

class ReplyForgeApp : Application() {

    val appPrefs: AppPrefs by lazy { AppPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "replyforge_service"
    }
}
