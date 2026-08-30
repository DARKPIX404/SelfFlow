package com.selfflow.app.service.alarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.selfflow.app.R
import com.selfflow.app.service.notification.NotificationHelper
import com.selfflow.app.service.overlay.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper

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

        val overlayIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TITLE, title)
            putExtra(OverlayService.EXTRA_CONTENT, content)
        }
        startService(overlayIntent)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_CONTENT = "alarm_content"
    }
}
