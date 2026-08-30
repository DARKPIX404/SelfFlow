package com.selfflow.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.selfflow.app.data.preferences.PreferencesKeys
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val wakeTime: Flow<LocalTime> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.WAKE_TIME]?.let { LocalTime.parse(it) }
            ?: DEFAULT_WAKE_TIME
    }

    val sleepTime: Flow<LocalTime> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SLEEP_TIME]?.let { LocalTime.parse(it) }
            ?: DEFAULT_SLEEP_TIME
    }

    val dynamicThemeEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DYNAMIC_THEME_ENABLED] ?: true
    }

    val alarmEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ALARM_ENABLED] ?: true
    }

    suspend fun setWakeTime(time: LocalTime) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_TIME] = time.toString()
        }
    }

    suspend fun setSleepTime(time: LocalTime) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SLEEP_TIME] = time.toString()
        }
    }

    suspend fun setDynamicThemeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_THEME_ENABLED] = enabled
        }
    }

    suspend fun setAlarmEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALARM_ENABLED] = enabled
        }
    }

    companion object {
        val DEFAULT_WAKE_TIME = LocalTime.of(7, 0)
        val DEFAULT_SLEEP_TIME = LocalTime.of(23, 0)
    }
}
