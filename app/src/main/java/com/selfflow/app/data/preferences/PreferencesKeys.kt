package com.selfflow.app.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    val WAKE_TIME = stringPreferencesKey("wake_time")
    val SLEEP_TIME = stringPreferencesKey("sleep_time")
    val DYNAMIC_THEME_ENABLED = booleanPreferencesKey("dynamic_theme_enabled")
    val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
    val ROUTINE_TEMPLATES = stringPreferencesKey("routine_templates")
    val ALARM_RINGTONE = stringPreferencesKey("alarm_ringtone")
    val NOTIFICATION_SOUND = stringPreferencesKey("notification_sound")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}
