package com.wahyuzero.replyforge.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wahyuzero.replyforge.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class StatsState(
    val totalReplies: Int = 0,
    val repliesToday: Int = 0,
    val repliesContacts: Int = 0,
    val repliesGroups: Int = 0,
    val isLoading: Boolean = false
)

class StatsViewModel(private val db: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow(StatsState())
    val state = _state.asStateFlow()

    init {
        loadStats()
    }

    fun refresh() {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val endOfDay = cal.timeInMillis

            val totalReplies = db.historyDao().getTotalReplies()
            val repliesToday = db.historyDao().getRepliesToday(startOfDay, endOfDay)
            val repliesContacts = db.historyDao().getRepliesToContacts()
            val repliesGroups = db.historyDao().getRepliesToGroups()

            _state.value = StatsState(
                totalReplies = totalReplies,
                repliesToday = repliesToday,
                repliesContacts = repliesContacts,
                repliesGroups = repliesGroups,
                isLoading = false
            )
        }
    }
}
