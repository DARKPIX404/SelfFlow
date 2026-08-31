package com.selfflow.app

import android.app.Application
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.service.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SelfFlowApplication : Application() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        val notificationSound = runBlocking(Dispatchers.IO) {
            settingsRepository.notificationSound.first()
        }
        notificationHelper.createNotificationChannels(notificationSound)
    }
}
