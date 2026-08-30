package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.service.alarm.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    val dynamicThemeEnabled: StateFlow<Boolean> = settingsRepository.dynamicThemeEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = true
        )

    fun setWakeTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setWakeTime(time)
            rescheduleIfEnabled(time, settingsRepository.sleepTime.first())
        }
    }

    fun setSleepTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setSleepTime(time)
            rescheduleIfEnabled(settingsRepository.wakeTime.first(), time)
        }
    }

    fun setDynamicThemeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicThemeEnabled(enabled)
        }
    }

    private suspend fun rescheduleIfEnabled(wake: LocalTime, sleep: LocalTime) {
        if (settingsRepository.alarmEnabled.first()) {
            alarmScheduler.scheduleWakeAlarm(wake)
            alarmScheduler.scheduleSleepAlarm(sleep)
        }
    }
}
