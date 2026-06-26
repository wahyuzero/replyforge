package com.wahyuzero.replyforge.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wahyuzero.replyforge.data.db.AppDatabase

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(application)
        return when (modelClass) {
            RulesViewModel::class.java -> RulesViewModel(db) as T
            HistoryViewModel::class.java -> HistoryViewModel(db) as T
            StatsViewModel::class.java -> StatsViewModel(db) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}
