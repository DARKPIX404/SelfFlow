package com.selfflow.app.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.selfflow.app.R
import com.selfflow.app.data.notification.NotificationSoundOption
import com.selfflow.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val manager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun createNotificationChannels(soundOption: NotificationSoundOption = NotificationSoundOption.SYSTEM_DEFAULT) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val routineChannel = NotificationChannel(
            CHANNEL_ROUTINES,
            context.getString(R.string.notification_channel_routines_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_routines_description)
            setSound(soundOption.uri(context), audioAttributes())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.notification_channel_alarms_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarms_description)
            setBypassDnd(true)
        }

        manager.createNotificationChannels(listOf(routineChannel, alarmChannel))
    }

    fun updateRoutineChannel(soundOption: NotificationSoundOption) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.deleteNotificationChannel(CHANNEL_ROUTINES)
        createNotificationChannels(soundOption)
    }

    fun getAlarmNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(contentPendingIntent(), true)
            .setContentIntent(contentPendingIntent())
            .build()
    }

    fun showRoutineReminder(
        routineId: Long,
        title: String,
        content: String,
        soundOption: NotificationSoundOption = NotificationSoundOption.SYSTEM_DEFAULT
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ROUTINES)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(contentPendingIntent())

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundOption.uri(context))
        }

        manager.notify(routineId.toInt(), builder.build())
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            pendingIntentFlags()
        )
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        const val CHANNEL_ROUTINES = "routine_reminders"
        const val CHANNEL_ALARM = "alarm_channel"
        const val NOTIFICATION_ID_ALARM = 1001
    }
}

private fun audioAttributes(): AudioAttributes {
    return AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}

fun NotificationSoundOption.uri(context: Context): Uri? {
    return when (this) {
        NotificationSoundOption.SYSTEM_DEFAULT ->
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        else -> Uri.parse("android.resource://${context.packageName}/${rawRes}")
    }
}
