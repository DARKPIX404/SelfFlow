package com.selfflow.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Планировщик нативных будильников с оверлеем.
 * RTC_WAKEUP-алярмы через AlarmManager; одиночные — exact
 * (с фолбэком на setAndAllowWhileIdle), wake/sleep — setRepeating.
 * RequestCode стабильный: hash от id + суффикс типа.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleOneShot(spec: AlarmSpec) {
        cancel(spec.id)
        val pending = pendingIntent(spec.id, alarmIntent(spec))
        val triggerAt = spec.timeMillis
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canScheduleExact() ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else ->
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Ежедневный повторяющийся будильник (wake/sleep) на локальном времени час/минута.
     * setRepeating со времён API 19 неточный и в Doze сдвигается — ставим
     * точный one-shot, а AlarmReceiver при срабатывании планирует следующий день. */
    fun scheduleRepeating(id: String, hour: Int, minute: Int, title: String, text: String, sound: String) {
        cancel(id)
        val triggerAt = nextDailyMillis(hour, minute)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            data = Uri.parse("selfflow://alarm/$id")
            putExtra(AlarmReceiver.EXTRA_ID, id)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_TEXT, text)
            putExtra(AlarmReceiver.EXTRA_SOUND, sound)
            putExtra(AlarmReceiver.EXTRA_VIBRATE, true)
            putExtra(AlarmReceiver.EXTRA_REPEAT_HOUR, hour)
            putExtra(AlarmReceiver.EXTRA_REPEAT_MINUTE, minute)
        }
        val pending = pendingIntent(id, intent)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canScheduleExact() ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else ->
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(id: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            data = Uri.parse("selfflow://alarm/$id")
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode(id), intent,
            PendingIntent.FLAG_NO_CREATE or immutableFlag()
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    fun cancelAll(ids: Collection<String>) = ids.forEach(::cancel)

    private fun alarmIntent(spec: AlarmSpec): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            data = Uri.parse("selfflow://alarm/${spec.id}")
            putExtra(AlarmReceiver.EXTRA_ID, spec.id)
            putExtra(AlarmReceiver.EXTRA_TITLE, spec.title)
            putExtra(AlarmReceiver.EXTRA_TEXT, spec.text)
            putExtra(AlarmReceiver.EXTRA_SOUND, spec.sound)
            putExtra(AlarmReceiver.EXTRA_VIBRATE, spec.vibrate)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, spec.snoozeMinutes)
        }

    private fun pendingIntent(id: String, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode(id), intent, immutableFlag() or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun requestCode(id: String): Int = id.hashCode()

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun nextDailyMillis(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

/** Одиночный будильник (данные из JS). */
data class AlarmSpec(
    val id: String,
    val timeMillis: Long,
    val title: String,
    val text: String,
    val sound: String,
    val vibrate: Boolean,
    val snoozeMinutes: Int,
)
