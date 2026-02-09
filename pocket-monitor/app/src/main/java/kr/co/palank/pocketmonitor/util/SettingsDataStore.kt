package kr.co.palank.pocketmonitor.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val DAILY_REPORT = booleanPreferencesKey("daily_report")
    val TEMP_ALERT = booleanPreferencesKey("temp_alert")
    val WEEKLY_REPORT = booleanPreferencesKey("weekly_report")
}

class SettingsDataStore(private val context: Context) {

    fun getBoolean(key: Preferences.Key<Boolean>, defaultValue: Boolean = true): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[key] ?: defaultValue
        }
    }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[key] = value
        }
    }
}
