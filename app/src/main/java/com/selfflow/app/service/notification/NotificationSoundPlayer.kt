package com.selfflow.app.service.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.selfflow.app.data.notification.NotificationSoundOption

class NotificationSoundPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun play(option: NotificationSoundOption) {
        stop()
        val uri = option.uri(context) ?: return
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            try {
                setDataSource(context, uri)
                prepare()
                start()
                setOnCompletionListener { releasePlayer() }
            } catch (e: Exception) {
                releasePlayer()
            }
        }
    }

    fun stop() {
        releasePlayer()
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
