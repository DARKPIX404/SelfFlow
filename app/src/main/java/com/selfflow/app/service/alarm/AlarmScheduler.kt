package com.selfflow.app.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.selfflow.app.data.notification.NotificationSoundOption
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.domain.model.RecurrenceRule
import com.selfflow.app.domain.model.Routine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val settingsRepository: SettingsRepository
) {

    fun scheduleRoutineAlarm(routine: Routine) {
        cancelRoutineAlarm(routine.id)
        if (!routine.isActive) return

        val occurrences = calculateOccurrences(routine)
        occurrences.forEachIndexed { index, instant ->
            scheduleRoutineOccurrence(
                routine = routine,
                occurrenceIndex = index + 1,
                triggerAtMillis = instant.toEpochMilli()
            )
        }
    }

    fun cancelRoutineAlarm(routineId: Long) {
        for (index in 0..MAX_OCCURRENCES) {
            val pendingIntent = routinePendingIntent(routineId, index)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleWakeAlarm(time: LocalTime) {
        scheduleDaily(
            action = ACTION_WAKE_ALARM,
            requestCode = REQUEST_CODE_WAKE,
            time = time,
            title = context.getString(com.selfflow.app.R.string.app_name),
            content = context.getString(com.selfflow.app.R.string.wake_alarm_content)
        )
    }

    fun scheduleSleepAlarm(time: LocalTime) {
        scheduleDaily(
            action = ACTION_SLEEP_ALARM,
            requestCode = REQUEST_CODE_SLEEP,
            time = time,
            title = context.getString(com.selfflow.app.R.string.app_name),
            content = context.getString(com.selfflow.app.R.string.sleep_alarm_content)
        )
    }

    fun cancelWakeAlarm() = cancelDaily(REQUEST_CODE_WAKE, ACTION_WAKE_ALARM)

    fun cancelSleepAlarm() = cancelDaily(REQUEST_CODE_SLEEP, ACTION_SLEEP_ALARM)

    fun rescheduleWakeSleepAlarms() {
        val (enabled, wake, sleep) = runBlocking(Dispatchers.IO) {
            Triple(
                settingsRepository.alarmEnabled.first(),
                settingsRepository.wakeTime.first(),
                settingsRepository.sleepTime.first()
            )
        }
        if (!enabled) return
        scheduleWakeAlarm(wake)
        scheduleSleepAlarm(sleep)
    }

    private fun scheduleRoutineOccurrence(
        routine: Routine,
        occurrenceIndex: Int,
        triggerAtMillis: Long
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ROUTINE_ALARM
            putExtra(EXTRA_ROUTINE_ID, routine.id)
            putExtra(EXTRA_TITLE, routine.title)
            putExtra(EXTRA_NOTIFICATION_TEXT, routine.notificationText ?: routine.title)
            putExtra(EXTRA_NOTIFICATION_SOUND, currentNotificationSound().key)
            data = Uri.parse("selfflow://routine/${routine.id}/$occurrenceIndex")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            routineRequestCode(routine.id, occurrenceIndex),
            intent,
            pendingIntentFlags()
        )

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canScheduleExact() -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            else -> {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    private fun calculateOccurrences(routine: Routine): List<Instant> {
        val zone = ZoneId.systemDefault()
        val base = ZonedDateTime.ofInstant(routine.startTime, zone)
        val baseLocal = base.toLocalDateTime()

        return when (routine.recurrenceRule) {
            RecurrenceRule.NONE -> {
                if (base.toInstant().isAfter(Instant.now())) {
                    listOf(base.toInstant())
                } else {
                    emptyList()
                }
            }
            RecurrenceRule.DAILY -> generateOccurrences(
                baseLocal = baseLocal,
                zone = zone,
                count = DAILY_OCCURRENCES
            ) { it.plusDays(1) }
            RecurrenceRule.WEEKDAYS -> generateWeekdayOccurrences(baseLocal, zone, WEEKDAY_OCCURRENCES)
            RecurrenceRule.WEEKLY -> generateOccurrences(
                baseLocal = baseLocal,
                zone = zone,
                count = WEEKLY_OCCURRENCES
            ) { it.plusWeeks(1) }
        }
    }

    private fun generateOccurrences(
        baseLocal: LocalDateTime,
        zone: ZoneId,
        count: Int,
        next: (LocalDateTime) -> LocalDateTime
    ): List<Instant> {
        val result = mutableListOf<Instant>()
        var current = baseLocal
        val now = Instant.now()
        var added = 0

        while (added < count) {
            val instant = current.atZone(zone).toInstant()
            if (instant.isAfter(now)) {
                result.add(instant)
                added++
            }
            current = next(current)
        }
        return result
    }

    private fun generateWeekdayOccurrences(
        baseLocal: LocalDateTime,
        zone: ZoneId,
        count: Int
    ): List<Instant> {
        val result = mutableListOf<Instant>()
        var current = baseLocal
        val now = Instant.now()

        while (result.size < count) {
            if (current.dayOfWeek != DayOfWeek.SATURDAY && current.dayOfWeek != DayOfWeek.SUNDAY) {
                val instant = current.atZone(zone).toInstant()
                if (instant.isAfter(now)) {
                    result.add(instant)
                }
            }
            current = current.plusDays(1)
        }
        return result
    }

    private fun routinePendingIntent(routineId: Long, occurrenceIndex: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ROUTINE_ALARM
            data = Uri.parse("selfflow://routine/$routineId/$occurrenceIndex")
        }
        return PendingIntent.getBroadcast(
            context,
            routineRequestCode(routineId, occurrenceIndex),
            intent,
            pendingIntentFlags()
        )
    }

    private fun routineRequestCode(routineId: Long, occurrenceIndex: Int): Int {
        return (routineId * REQUEST_CODE_MULTIPLIER + occurrenceIndex).toInt()
    }

    private fun scheduleDaily(
        action: String,
        requestCode: Int,
        time: LocalTime,
        title: String,
        content: String
    ) {
        cancelDaily(requestCode, action)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_NOTIFICATION_TEXT, content)
            data = Uri.parse("selfflow://$action")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags()
        )

        val triggerAtMillis = nextOccurrenceMillis(time)

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun cancelDaily(requestCode: Int, action: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("selfflow://$action")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags()
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun nextOccurrenceMillis(time: LocalTime): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var target = now.with(time)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }

    private fun currentNotificationSound(): NotificationSoundOption =
        runBlocking(Dispatchers.IO) { settingsRepository.notificationSound.first() }

    private fun canScheduleExact(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        const val ACTION_ROUTINE_ALARM = "com.selfflow.app.ACTION_ROUTINE_ALARM"
        const val ACTION_WAKE_ALARM = "com.selfflow.app.ACTION_WAKE_ALARM"
        const val ACTION_SLEEP_ALARM = "com.selfflow.app.ACTION_SLEEP_ALARM"

        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_NOTIFICATION_TEXT = "notification_text"
        const val EXTRA_NOTIFICATION_SOUND = "notification_sound"

        private const val REQUEST_CODE_WAKE = 9001
        private const val REQUEST_CODE_SLEEP = 9002

        private const val REQUEST_CODE_MULTIPLIER = 1_000_000
        private const val MAX_OCCURRENCES = 14
        private const val DAILY_OCCURRENCES = 14
        private const val WEEKDAY_OCCURRENCES = 10
        private const val WEEKLY_OCCURRENCES = 10
    }
}
