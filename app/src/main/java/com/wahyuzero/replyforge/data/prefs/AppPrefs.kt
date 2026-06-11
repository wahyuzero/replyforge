package com.wahyuzero.replyforge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPrefs(private val context: Context) {

    private object Keys {
        val AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")
        // Phase 3: Away mode
        val AWAY_MODE = booleanPreferencesKey("away_mode")
        val AWAY_MESSAGE = stringPreferencesKey("away_message")
    }

    val autoReplyEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_REPLY_ENABLED] ?: true
    }

    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVICE_ENABLED] ?: false
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    val welcomeDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WELCOME_DONE] ?: false
    }

    // Phase 3: Away mode
    val awayMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AWAY_MODE] ?: false
    }

    val awayMessage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.AWAY_MESSAGE] ?: ""
    }

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_REPLY_ENABLED] = enabled
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setWelcomeDone(done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WELCOME_DONE] = done
        }
    }

    // Phase 3: Away mode
    suspend fun setAwayMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AWAY_MODE] = enabled
        }
    }

    suspend fun setAwayMessage(message: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AWAY_MESSAGE] = message
        }
    }
}
