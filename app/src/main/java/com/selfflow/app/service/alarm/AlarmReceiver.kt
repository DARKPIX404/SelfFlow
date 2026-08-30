package com.selfflow.app.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

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

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_TITLE, title)
            putExtra(AlarmService.EXTRA_CONTENT, content)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
