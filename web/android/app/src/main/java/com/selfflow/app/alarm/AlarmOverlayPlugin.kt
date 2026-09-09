package com.selfflow.app.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import androidx.activity.result.ActivityResult
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * JS → native API оверлей-будильника:
 * scheduleAlarm / cancelAlarm — одиночные будильники с оверлеем;
 * rescheduleAll — wake/sleep-повторы + кэш расписания для BootReceiver;
 * checkOverlayPermission / requestOverlayPermission — SYSTEM_ALERT_WINDOW;
 * showOverlayNow — превью оверлея из настроек;
 * checkPermissions — сводка exact alarms / уведомления / поверх окон.
 */
@CapacitorPlugin(name = "AlarmOverlay")
class AlarmOverlayPlugin : Plugin() {

    private val scheduler get() = AlarmScheduler(context)

    @PluginMethod
    fun scheduleAlarm(call: PluginCall) {
        val id = call.getString("id") ?: return call.reject("id is required")
        val timeIso = call.getString("timeIso") ?: return call.reject("timeIso is required")
        val timeMillis = parseIso(timeIso) ?: return call.reject("timeIso is not a valid ISO timestamp: $timeIso")

        scheduler.scheduleOneShot(
            AlarmSpec(
                id = id,
                timeMillis = timeMillis,
                title = call.getString("title") ?: "SelfFlow",
                text = call.getString("text") ?: "",
                sound = call.getString("sound") ?: OverlayActivity.SOUND_SYSTEM,
                vibrate = call.getBoolean("vibrate", true)!!,
                snoozeMinutes = call.getInt("snoozeMinutes") ?: AlarmReceiver.DEFAULT_SNOOZE_MINUTES,
            )
        )
        call.resolve()
    }

    @PluginMethod
    fun cancelAlarm(call: PluginCall) {
        val id = call.getString("id") ?: return call.reject("id is required")
        scheduler.cancel(id)
        call.resolve()
    }

    @PluginMethod
    fun rescheduleAll(call: PluginCall) {
        val root = JSONObject()
        val repeating = JSONArray()
        val oneShot = JSONArray()

        val arr = call.getArray("repeating")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val id = item.getString("id")
                val hour = item.getInt("hour")
                val minute = item.getInt("minute")
                val title = item.optString("title", "SelfFlow")
                val text = item.optString("text", "")
                val sound = item.optString("sound", OverlayActivity.SOUND_SYSTEM)
                scheduler.scheduleRepeating(id, hour, minute, title, text, sound)
                repeating.put(item)
            }
        }

        val oneShotArr = call.getArray("oneShot")
        if (oneShotArr != null) {
            for (i in 0 until oneShotArr.length()) {
                val item = oneShotArr.getJSONObject(i)
                val spec = AlarmSpec(
                    id = item.getString("id"),
                    timeMillis = item.getLong("timeMillis"),
                    title = item.optString("title", "SelfFlow"),
                    text = item.optString("text", ""),
                    sound = item.optString("sound", OverlayActivity.SOUND_SYSTEM),
                    vibrate = item.optBoolean("vibrate", true),
                    snoozeMinutes = item.optInt("snoozeMinutes", AlarmReceiver.DEFAULT_SNOOZE_MINUTES),
                )
                scheduler.scheduleOneShot(spec)
                oneShot.put(item)
            }
        }

        // кэшируем для BootReceiver (повторы переживают reboot сами,
        // но одиночные нужно перепланировать)
        root.put(BootReceiver.KEY_REPEATING, repeating)
        root.put(BootReceiver.KEY_ONE_SHOT, oneShot)
        context.getSharedPreferences(BootReceiver.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(BootReceiver.KEY_PAYLOAD, root.toString())
            .apply()

        call.resolve(permissionsResult())
    }

    @PluginMethod
    fun checkOverlayPermission(call: PluginCall) {
        call.resolve(JSObject().put("granted", canDrawOverlays()))
    }

    @PluginMethod
    fun requestOverlayPermission(call: PluginCall) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        startActivityForResult(call, intent, "onOverlayPermissionResult")
    }

    @ActivityCallback
    private fun onOverlayPermissionResult(call: PluginCall, result: ActivityResult) {
        call.resolve(JSObject().put("granted", canDrawOverlays()))
    }

    @PluginMethod
    fun showOverlayNow(call: PluginCall) {
        OverlayActivity.start(
            context,
            call.getString("title") ?: "SelfFlow",
            call.getString("text") ?: "",
        )
        call.resolve()
    }

    /** Переопределяем базовый Plugin.checkPermissions — сводка по нашим трём правам. */
    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        call.resolve(permissionsResult())
    }

    @PluginMethod
    fun requestExactAlarmPermission(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivityForResult(call, Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM"), "onExactAlarmResult")
        } else {
            call.resolve(JSObject().put("granted", true))
        }
    }

    @ActivityCallback
    private fun onExactAlarmResult(call: PluginCall, result: ActivityResult) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        call.resolve(JSObject().put("granted", exact))
    }

    private fun permissionsResult(): JSObject {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        return JSObject()
            .put("exactAlarms", exact)
            .put("notifications", notifications)
            .put("overlay", canDrawOverlays())
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun parseIso(iso: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(iso)?.time
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val REQ_OVERLAY = 7101
        private const val REQ_EXACT = 7102
    }
}
