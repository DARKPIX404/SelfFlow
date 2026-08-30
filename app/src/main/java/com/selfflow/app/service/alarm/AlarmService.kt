package com.selfflow.app.service.alarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.selfflow.app.R
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.presentation.screens.alarm.AlarmOverlayActivity
import com.selfflow.app.service.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        val content = intent?.getStringExtra(EXTRA_CONTENT) ?: "Alarm"

        val notification = notificationHelper.getAlarmNotification(title, content)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_ALARM,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_ALARM, notification)
        }

        val ringtone = runBlocking(Dispatchers.IO) {
            settingsRepository.alarmRingtone.first()
        }

        startActivity(
            AlarmOverlayActivity.createIntent(
                context = this,
                title = title,
                content = content,
                ringtone = ringtone
            )
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_CONTENT = "alarm_content"
    }
}
