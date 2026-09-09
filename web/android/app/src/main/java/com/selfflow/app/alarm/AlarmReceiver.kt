package com.selfflow.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Срабатывание будильника: поднимаем foreground-сервис — он показывает
 * ongoing-уведомление с fullScreenIntent на OverlayActivity.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return

        // ежедневный будильник: перепланируем следующий день (exact one-shot
        // вместо неточного setRepeating — иначе в Doze сдвигается на часы)
        val repeatHour = intent.getIntExtra(EXTRA_REPEAT_HOUR, -1)
        val repeatMinute = intent.getIntExtra(EXTRA_REPEAT_MINUTE, -1)
        if (repeatHour >= 0 && repeatMinute >= 0) {
            AlarmScheduler(context).scheduleRepeating(
                id = intent.getStringExtra(EXTRA_ID) ?: "alarm",
                hour = repeatHour,
                minute = repeatMinute,
                title = intent.getStringExtra(EXTRA_TITLE) ?: "SelfFlow",
                text = intent.getStringExtra(EXTRA_TEXT) ?: "",
                sound = intent.getStringExtra(EXTRA_SOUND) ?: OverlayActivity.SOUND_SYSTEM,
            )
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(EXTRA_ID, intent.getStringExtra(EXTRA_ID))
            putExtra(EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE))
            putExtra(EXTRA_TEXT, intent.getStringExtra(EXTRA_TEXT))
            putExtra(EXTRA_SOUND, intent.getStringExtra(EXTRA_SOUND))
            putExtra(EXTRA_VIBRATE, intent.getBooleanExtra(EXTRA_VIBRATE, true))
            putExtra(EXTRA_SNOOZE_MINUTES, intent.getIntExtra(EXTRA_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES))
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ACTION_ALARM = "com.selfflow.app.alarm.ACTION_ALARM"

        const val EXTRA_ID = "alarm_id"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_TEXT = "alarm_text"
        const val EXTRA_SOUND = "alarm_sound"
        const val EXTRA_VIBRATE = "alarm_vibrate"
        const val EXTRA_SNOOZE_MINUTES = "alarm_snooze_minutes"
        const val EXTRA_REPEAT_HOUR = "alarm_repeat_hour"
        const val EXTRA_REPEAT_MINUTE = "alarm_repeat_minute"

        const val DEFAULT_SNOOZE_MINUTES = 5
    }
}
