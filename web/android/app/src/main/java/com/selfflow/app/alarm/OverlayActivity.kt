package com.selfflow.app.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
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
    private var sound: String = SOUND_SYSTEM
    private var snoozeMinutes: Int = AlarmReceiver.DEFAULT_SNOOZE_MINUTES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setMaxBrightness()

        alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ID) ?: "alarm"
        title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: "SelfFlow"
        text = intent.getStringExtra(AlarmReceiver.EXTRA_TEXT) ?: ""
        sound = intent.getStringExtra(AlarmReceiver.EXTRA_SOUND) ?: SOUND_SYSTEM
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

        val cream = 0xFFF0E6CC.toInt()

        val labelView = TextView(this).apply {
            text = "Б У Д И Л Ь Н И К"
            setTextColor(0x80F0E6CC.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        val timeText = TextView(this).apply {
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            setTextColor(cream)
            textSize = 84f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 10.dp(), 0, 6.dp())
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(cream)
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val textView = TextView(this).apply {
            text = text
            setTextColor(0x99F0E6CC.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 6.dp(), 0, 0)
        }

        fun roundButton(bgColor: Int, strokeColor: Int?, radius: Int): android.graphics.drawable.Drawable {
            val d = android.graphics.drawable.GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = radius.dp().toFloat()
                if (strokeColor != null) setStroke(2.dp(), strokeColor)
            }
            return d
        }

        val dismissBtn = Button(this).apply {
            text = "ВЫКЛЮЧИТЬ"
            setTextColor(cream)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = roundButton(0xFFC9403B.toInt(), null, 18)
            setOnClickListener { dismissAlarm() }
        }

        val snoozeBtn = Button(this).apply {
            text = "Отложить (+$snoozeMinutes мин)"
            setTextColor(cream)
            textSize = 15f
            background = roundButton(0x14F0E6CC.toInt(), 0x66F0E6CC.toInt(), 18)
            setOnClickListener { snoozeAlarm() }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(dismissBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 56.dp()).apply { topMargin = 26.dp() })
            addView(snoozeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 50.dp()).apply { topMargin = 12.dp() })
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF242019.toInt())
                cornerRadius = 30.dp().toFloat()
                setStroke(1.dp(), 0x26F0E6CC.toInt())
            }
            setPadding(26.dp(), 30.dp(), 26.dp(), 26.dp())
            addView(labelView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(timeText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(textView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        // корень: тёмный полупрозрачный фон + центрированная карточка
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE6100E0C.toInt())
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
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
        const val SOUND_SYSTEM = "system"
        const val SOUND_LOFI_MORNING = "lofi_morning"
        const val SOUND_LOFI_CLOUDS = "lofi_clouds"
        const val SOUND_LOFI_NIGHT = "lofi_night"
        const val SOUND_LOFI_CHIME = "lofi_chime"
        const val SOUND_LOFI_PLUCK = "lofi_pluck"

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
            putExtra(AlarmReceiver.EXTRA_SOUND, sound ?: SOUND_SYSTEM)
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
