package com.selfflow.app.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Полноэкранный оверлей будильника поверх lock-screen.
 * Палитра SelfFlow: #151311 фон, #F0E6CC текст, #C9403B акцент.
 * Рингтон лупится, вибрация — waveform; оба ОБЯЗАТЕЛЬНО останавливаются
 * при любом выходе (onDestroy). «Отложить» реально перепланирует будильник.
 */
class OverlayActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var alarmId: String = "alarm"
    private var title: String = "SelfFlow"
    private var text: String = ""
    private var sound: String = SOUND_ALARM_STANDARD
    private var snoozeMinutes: Int = AlarmReceiver.DEFAULT_SNOOZE_MINUTES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setMaxBrightness()

        alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ID) ?: "alarm"
        title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: "SelfFlow"
        text = intent.getStringExtra(AlarmReceiver.EXTRA_TEXT) ?: ""
        sound = intent.getStringExtra(AlarmReceiver.EXTRA_SOUND) ?: SOUND_ALARM_STANDARD
        snoozeMinutes = intent.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, AlarmReceiver.DEFAULT_SNOOZE_MINUTES)

        setContentView(buildContent())

        startRingtone()
        if (intent.getBooleanExtra(AlarmReceiver.EXTRA_VIBRATE, true)) startVibration()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, null)
        }
    }

    private fun setMaxBrightness() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = lp
    }

    private fun buildContent(): LinearLayout {
        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val timeText = TextView(this).apply {
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            setTextColor(0xFFF0E6CC.toInt())
            textSize = 88f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8.dp())
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(0xFFF0E6CC.toInt())
            textSize = 26f
            gravity = Gravity.CENTER
        }

        val textView = TextView(this).apply {
            text = text
            setTextColor(0x99F0E6CC.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 0)
        }

        val dismissBtn = Button(this).apply {
            text = "Выключить"
            setTextColor(0xFFF0E6CC.toInt())
            textSize = 18f
            setBackgroundColor(0xFFC9403B.toInt())
            setOnClickListener { dismissAlarm() }
        }

        val snoozeBtn = Button(this).apply {
            text = "Отложить (+$snoozeMinutes мин)"
            setTextColor(0xFFF0E6CC.toInt())
            textSize = 16f
            setBackgroundColor(0x33F0E6CC.toInt())
            setOnClickListener { snoozeAlarm() }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(dismissBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 56.dp()).apply { topMargin = 24.dp() })
            addView(snoozeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp()).apply { topMargin = 12.dp() })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF151311.toInt())
            setPadding(32.dp(), 32.dp(), 32.dp(), 32.dp())
            addView(timeText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(textView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun ringtoneRes(sound: String): Int = when (sound) {
        SOUND_ALARM_STANDARD -> com.selfflow.app.R.raw.alarm_standard
        SOUND_LOFI_CHIME -> com.selfflow.app.R.raw.lofi_chime
        SOUND_LOFI_PLUCK -> com.selfflow.app.R.raw.lofi_pluck
        SOUND_DIGITAL_BEEP -> com.selfflow.app.R.raw.digital_beep
        SOUND_CLASSIC_BELL -> com.selfflow.app.R.raw.classic_bell
        else -> com.selfflow.app.R.raw.alarm_standard
    }

    private fun startRingtone() {
        stopRingtone()
        val res = ringtoneRes(sound)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(resources.openRawResourceFd(res))
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopRingtone() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 600, 250, 600, 250, 900)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun dismissAlarm() {
        stopService(Intent(this, AlarmService::class.java))
        stopAlarmEffects()
        finish()
    }

    private fun snoozeAlarm() {
        val scheduler = AlarmScheduler(this)
        val snoozedId = "$alarmId:snooze"
        val at = System.currentTimeMillis() + snoozeMinutes * 60_000L
        scheduler.scheduleOneShot(
            AlarmSpec(
                id = snoozedId,
                timeMillis = at,
                title = title,
                text = text,
                sound = sound,
                vibrate = intent.getBooleanExtra(AlarmReceiver.EXTRA_VIBRATE, true),
                snoozeMinutes = snoozeMinutes,
            )
        )
        stopService(Intent(this, AlarmService::class.java))
        stopAlarmEffects()
        finish()
    }

    private fun stopAlarmEffects() {
        stopRingtone()
        stopVibration()
    }

    override fun onDestroy() {
        stopAlarmEffects()
        super.onDestroy()
    }

    companion object {
        const val SOUND_ALARM_STANDARD = "alarm_standard"
        const val SOUND_LOFI_CHIME = "lofi_chime"
        const val SOUND_LOFI_PLUCK = "lofi_pluck"
        const val SOUND_DIGITAL_BEEP = "digital_beep"
        const val SOUND_CLASSIC_BELL = "classic_bell"

        fun intent(
            context: Context,
            id: String,
            title: String,
            text: String,
            sound: String?,
            vibrate: Boolean,
            snoozeMinutes: Int,
        ): Intent = Intent(context, OverlayActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, id)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_TEXT, text)
            putExtra(AlarmReceiver.EXTRA_SOUND, sound ?: SOUND_ALARM_STANDARD)
            putExtra(AlarmReceiver.EXTRA_VIBRATE, vibrate)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        fun start(context: Context, title: String, text: String) {
            context.startActivity(intent(context, "preview", title, text, null, true, AlarmReceiver.DEFAULT_SNOOZE_MINUTES))
        }
    }
}
