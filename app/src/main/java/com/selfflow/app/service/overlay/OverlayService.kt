package com.selfflow.app.service.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private var overlayView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView != null) return START_STICKY

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "SelfFlow"
        val content = intent?.getStringExtra(EXTRA_CONTENT) ?: "Alarm"

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            gravity = Gravity.CENTER
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val contentView = TextView(this).apply {
            text = content
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val dismissButton = Button(this).apply {
            text = getString(com.selfflow.app.R.string.overlay_dismiss_button)
            setOnClickListener {
                stopService(Intent(this@OverlayService, com.selfflow.app.service.alarm.AlarmService::class.java))
                stopSelf()
            }
        }

        container.addView(titleView)
        container.addView(contentView)
        container.addView(dismissButton)

        overlayView = container
        windowManager.addView(container, layoutParams)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { view ->
            try {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // View was already removed
            }
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TITLE = "overlay_title"
        const val EXTRA_CONTENT = "overlay_content"
    }
}
