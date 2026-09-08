package com.selfflow.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground-сервис (dataSync) будильника: ongoing-уведомление с
 * fullScreenIntent на OverlayActivity. Сервис гарантированно останавливается
 * по dismiss/snooze (OverlayActivity шлёт stopService + stopSelf здесь),
 * а рестарт START_STICKY с null-intent оверлей не поднимает — сразу stopSelf.
 */
class AlarmService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // рестарт системой без данных — оверлей не поднимаем
            stopSelf()
            return START_STICKY
        }

        val title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: "SelfFlow"
        val text = intent.getStringExtra(AlarmReceiver.EXTRA_TEXT) ?: ""
        val id = intent.getStringExtra(AlarmReceiver.EXTRA_ID) ?: "alarm"

        val notification = buildNotification(id, title, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildNotification(id: String, title: String, text: String): Notification {
        val overlayIntent = OverlayActivity.intent(
            context = this,
            id = id,
            title = title,
            text = text,
            sound = null, // звук играет сама OverlayActivity
            vibrate = false,
            snoozeMinutes = AlarmReceiver.DEFAULT_SNOOZE_MINUTES,
        )
        val fullScreenPending = PendingIntent.getActivity(
            this, 0, overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        ensureChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Будильник",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Оверлей будильника поверх экрана блокировки"
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "alarm_overlay"
        const val NOTIFICATION_ID = 42
    }
}
