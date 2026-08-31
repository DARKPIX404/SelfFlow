package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.notification.NotificationSoundOption
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.data.security.SecureStorage
import com.selfflow.app.service.alarm.AlarmScheduler
import com.selfflow.app.service.notification.NotificationHelper
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
    private val alarmScheduler: AlarmScheduler,
    private val secureStorage: SecureStorage,
    private val notificationHelper: NotificationHelper
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

    val appLockEnabled: StateFlow<Boolean> = secureStorage.isAppLockEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val biometricEnabled: StateFlow<Boolean> = secureStorage.isBiometricEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val biometricAvailable: Boolean = secureStorage.isBiometricAvailable()

    val isPinSet: StateFlow<Boolean> = secureStorage.isPinSetFlow()

    val notificationSound: StateFlow<NotificationSoundOption> = settingsRepository.notificationSound
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = NotificationSoundOption.SYSTEM_DEFAULT
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

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            secureStorage.setAppLockEnabled(enabled)
            if (!enabled) {
                secureStorage.clearPin()
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            secureStorage.setBiometricEnabled(enabled)
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            secureStorage.setPin(pin)
            secureStorage.setAppLockEnabled(true)
        }
    }

    fun changePin(pin: String) {
        viewModelScope.launch {
            secureStorage.setPin(pin)
        }
    }

    fun setNotificationSound(option: NotificationSoundOption) {
        viewModelScope.launch {
            settingsRepository.setNotificationSound(option)
            notificationHelper.updateRoutineChannel(option)
        }
    }

    private suspend fun rescheduleIfEnabled(wake: LocalTime, sleep: LocalTime) {
        if (settingsRepository.alarmEnabled.first()) {
            alarmScheduler.scheduleWakeAlarm(wake)
            alarmScheduler.scheduleSleepAlarm(sleep)
        }
    }
}
