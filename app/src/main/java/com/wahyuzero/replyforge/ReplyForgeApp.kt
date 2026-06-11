package com.wahyuzero.replyforge

import android.app.Application
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.prefs.AppPrefs

class ReplyForgeApp : Application() {

    lateinit var appPrefs: AppPrefs
        private set

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        appPrefs = AppPrefs(this)
        database = AppDatabase.getInstance(this)
    }
}
