package com.selfflow.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import com.selfflow.app.R
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.TaskRepository
import com.selfflow.app.domain.model.Status
import com.selfflow.app.presentation.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class DailyWidget @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val routines = routineRepository.getAll().first()
        val tasks = taskRepository.getAll().first()

        val nextRoutine = routines
            .filter { it.startTime >= start && it.startTime < end && it.startTime > now }
            .minByOrNull { it.startTime }

        val remainingTasks = tasks.count { task ->
            val due = task.dueDate
            due != null && due >= start && due < end && task.status != Status.DONE
        }

        val routineText = nextRoutine?.let {
            val time = it.startTime.atZone(zone).toLocalTime()
            "${TIME_FORMATTER.format(time)} — ${it.title}"
        } ?: "—"

        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .clickable(
                            actionStartActivity(Intent(context, MainActivity::class.java))
                        ),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.app_name),
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = context.getString(R.string.widget_next_routine_label),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp
                            ),
                            modifier = GlanceModifier.padding(top = 12.dp)
                        )
                        Text(
                            text = routineText,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = GlanceModifier.padding(top = 2.dp)
                        )
                        Text(
                            text = context.getString(R.string.widget_remaining_tasks, remainingTasks),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 14.sp
                            ),
                            modifier = GlanceModifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))

        suspend fun update(context: Context) {
            GlanceAppWidgetManager(context).requestUpdate(DailyWidget::class.java)
        }
    }
}
