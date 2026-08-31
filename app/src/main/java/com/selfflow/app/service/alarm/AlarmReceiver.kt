package com.selfflow.app.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.selfflow.app.data.notification.NotificationSoundOption
import com.selfflow.app.service.notification.NotificationHelper

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                AlarmScheduler.ACTION_ROUTINE_ALARM,
                AlarmScheduler.ACTION_WAKE_ALARM,
                AlarmScheduler.ACTION_SLEEP_ALARM
            )
        ) return

        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "SelfFlow"
        val content = intent.getStringExtra(AlarmScheduler.EXTRA_NOTIFICATION_TEXT)
            ?: "Routine reminder"

        when (intent.action) {
            AlarmScheduler.ACTION_ROUTINE_ALARM -> {
                val routineId = intent.getLongExtra(AlarmScheduler.EXTRA_ROUTINE_ID, -1L)
                val soundKey = intent.getStringExtra(AlarmScheduler.EXTRA_NOTIFICATION_SOUND)
                val soundOption = NotificationSoundOption.fromKey(soundKey)
                NotificationHelper(context).showRoutineReminder(
                    routineId = routineId,
                    title = title,
                    content = content,
                    soundOption = soundOption
                )
            }
            else -> {
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_TITLE, title)
                    putExtra(AlarmService.EXTRA_CONTENT, content)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
