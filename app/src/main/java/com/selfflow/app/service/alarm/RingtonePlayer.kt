package com.selfflow.app.service.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.selfflow.app.data.alarm.RingtoneOption

class RingtonePlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun play(option: RingtoneOption) {
        stop()

        val uri = when (option) {
            RingtoneOption.SYSTEM_DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            else -> Uri.parse("android.resource://${context.packageName}/${option.rawRes}")
        }

        if (uri == null) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(context, uri)
            isLooping = true
            prepare()
            start()
        }

        vibrate()
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun vibrate() {
        val currentVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = currentVibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            currentVibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 500),
                    0
                )
            )
        } else {
            @Suppress("DEPRECATION")
            currentVibrator.vibrate(longArrayOf(0, 500, 500), 0)
        }
    }
}
