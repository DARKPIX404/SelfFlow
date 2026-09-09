package com.selfflow.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Перепланирование после перезагрузки: JS при старте приложения передаёт
 * актуальное расписание (rescheduleAll), плагин кэширует его в SharedPreferences,
 * BootReceiver восстанавливает wake/sleep-повторы и будущие одиночные будильники.
 * Уведомления рутин/привычек (Capacitor Local Notifications) восстанавливает
 * собственный LocalNotificationRestoreReceiver плагина.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return
        val scheduler = AlarmScheduler(context)
        val now = System.currentTimeMillis()

        try {
            val root = JSONObject(payload)

            val repeating = root.optJSONArray(KEY_REPEATING) ?: JSONArray()
            for (i in 0 until repeating.length()) {
                val item = repeating.getJSONObject(i)
                scheduler.scheduleRepeating(
                    id = item.getString("id"),
                    hour = item.getInt("hour"),
                    minute = item.getInt("minute"),
                    title = item.optString("title", "SelfFlow"),
                    text = item.optString("text", ""),
                    sound = item.optString("sound", OverlayActivity.SOUND_ALARM_STANDARD),
                )
            }

            val oneShot = root.optJSONArray(KEY_ONE_SHOT) ?: JSONArray()
            for (i in 0 until oneShot.length()) {
                val item = oneShot.getJSONObject(i)
                val timeMillis = item.getLong("timeMillis")
                if (timeMillis <= now) continue
                scheduler.scheduleOneShot(
                    AlarmSpec(
                        id = item.getString("id"),
                        timeMillis = timeMillis,
                        title = item.optString("title", "SelfFlow"),
                        text = item.optString("text", ""),
                        sound = item.optString("sound", OverlayActivity.SOUND_ALARM_STANDARD),
                        vibrate = item.optBoolean("vibrate", true),
                        snoozeMinutes = item.optInt("snoozeMinutes", AlarmReceiver.DEFAULT_SNOOZE_MINUTES),
                    )
                )
            }
        } catch (_: Exception) {
            // битый кэш — JS перепланирует при следующем старте приложения
        }
    }

    companion object {
        const val PREFS = "selfflow_alarm_plugin"
        const val KEY_PAYLOAD = "reschedule_payload"
        const val KEY_REPEATING = "repeating"
        const val KEY_ONE_SHOT = "oneShot"
    }
}
