package com.selfflow.app.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.R
import com.selfflow.app.data.alarm.RingtoneOption
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.service.alarm.AlarmScheduler
import com.selfflow.app.service.alarm.AlarmService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val wakeTime: StateFlow<LocalTime> = settingsRepository.wakeTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SettingsRepository.DEFAULT_WAKE_TIME
        )

    val sleepTime: StateFlow<LocalTime> = settingsRepository.sleepTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SettingsRepository.DEFAULT_SLEEP_TIME
        )

    val alarmEnabled: StateFlow<Boolean> = settingsRepository.alarmEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = true
        )

    val alarmRingtone: StateFlow<RingtoneOption> = settingsRepository.alarmRingtone
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = RingtoneOption.SYSTEM_DEFAULT
        )

    fun setAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAlarmEnabled(enabled)
            val wake = settingsRepository.wakeTime.first()
            val sleep = settingsRepository.sleepTime.first()
            if (enabled) {
                alarmScheduler.scheduleWakeAlarm(wake)
                alarmScheduler.scheduleSleepAlarm(sleep)
            } else {
                alarmScheduler.cancelWakeAlarm()
                alarmScheduler.cancelSleepAlarm()
            }
        }
    }

    fun setAlarmRingtone(option: RingtoneOption) {
        viewModelScope.launch {
            settingsRepository.setAlarmRingtone(option)
        }
    }

    fun onTimesChanged(wake: LocalTime, sleep: LocalTime) {
        viewModelScope.launch {
            val enabled = settingsRepository.alarmEnabled.first()
            if (enabled) {
                alarmScheduler.scheduleWakeAlarm(wake)
                alarmScheduler.scheduleSleepAlarm(sleep)
            }
        }
    }

    fun testAlarm(context: Context) {
        val intent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_TITLE, context.getString(R.string.app_name))
            putExtra(AlarmService.EXTRA_CONTENT, context.getString(R.string.alarm_test_content))
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
