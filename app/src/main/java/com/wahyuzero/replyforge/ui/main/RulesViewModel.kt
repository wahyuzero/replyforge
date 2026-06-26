package com.wahyuzero.replyforge.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.model.Rule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RulesViewModel(private val db: AppDatabase) : ViewModel() {

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules = _rules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            _isLoading.value = true
            db.ruleDao().getAllRules().collect { rules ->
                _rules.value = rules
                _isLoading.value = false
            }
        }
    }

    fun toggleRule(rule: Rule, enabled: Boolean) {
        viewModelScope.launch {
            db.ruleDao().setEnabled(rule.id, enabled)
        }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            db.ruleDao().delete(rule)
        }
    }
}
