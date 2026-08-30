package com.selfflow.app.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.service.alarm.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val routines = routineRepository.getActiveRoutines().first()
                routines.forEach { alarmScheduler.scheduleRoutineAlarm(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
