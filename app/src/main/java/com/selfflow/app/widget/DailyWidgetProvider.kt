package com.selfflow.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DailyWidgetProvider : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var dailyWidget: DailyWidget

    override val glanceAppWidget: GlanceAppWidget
        get() = dailyWidget
}
