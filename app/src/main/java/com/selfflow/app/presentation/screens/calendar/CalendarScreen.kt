package com.selfflow.app.presentation.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.R
import com.selfflow.app.domain.model.Note
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Status
import com.selfflow.app.domain.model.Task
import com.selfflow.app.presentation.viewmodel.CalendarUiState
import com.selfflow.app.presentation.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val russianLocale = Locale("ru", "RU")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            MonthNavigationHeader(
                currentMonth = uiState.currentMonth,
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth
            )

            Spacer(modifier = Modifier.height(8.dp))

            WeekDaysHeader()

            Spacer(modifier = Modifier.height(4.dp))

            CalendarGrid(
                uiState = uiState,
                onDateSelected = viewModel::onDateSelected
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = uiState.selectedDate != null) {
                uiState.selectedDate?.let { date ->
                    DayDetailSection(
                        date = date,
                        routines = viewModel.routinesForDate(date),
                        tasks = viewModel.tasksForDate(date),
                        notes = viewModel.notesForDate(date),
                        onClearSelection = viewModel::onClearSelection
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MonthNavigationHeader(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val russianMonths = stringArrayResource(R.array.month_names)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month_content_description)
            )
        }

        Text(
            text = "${russianMonths[currentMonth.monthValue - 1]} ${currentMonth.year}",
            style = MaterialTheme.typography.titleLarge
        )

        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month_content_description)
            )
        }
    }
}

@Composable
private fun WeekDaysHeader() {
    val weekDays = stringArrayResource(R.array.week_days)

    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    uiState: CalendarUiState,
    onDateSelected: (LocalDate) -> Unit
) {
    val weeks = remember(uiState.currentMonth) {
        buildMonthDays(uiState.currentMonth).chunked(7)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { day ->
                    if (day != null) {
                        val hasRoutine = uiState.routines.any { it.occursOn(day) }
                        val hasTask = uiState.tasks.any { it.occursOn(day) }
                        val hasNote = uiState.notes.any { it.occursOn(day) }
                        val isSelected = uiState.selectedDate == day
                        val isToday = day == LocalDate.now()

                        DayCell(
                            date = day,
                            isSelected = isSelected,
                            isToday = isToday,
                            hasRoutine = hasRoutine,
                            hasTask = hasTask,
                            hasNote = hasNote,
                            onClick = { onDateSelected(day) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasRoutine: Boolean,
    hasTask: Boolean,
    hasNote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasRoutine) {
                    EventDot(color = MaterialTheme.colorScheme.primary)
                }
                if (hasTask) {
                    EventDot(color = MaterialTheme.colorScheme.secondary)
                }
                if (hasNote) {
                    EventDot(color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun EventDot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun DayDetailSection(
    date: LocalDate,
    routines: List<Routine>,
    tasks: List<Task>,
    notes: List<Note>,
    onClearSelection: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM", russianLocale) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClearSelection() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = date.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (routines.isEmpty() && tasks.isEmpty() && notes.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_no_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (routines.isNotEmpty()) {
                    DetailSectionTitle(
                        title = stringResource(R.string.calendar_routines_section),
                        color = MaterialTheme.colorScheme.primary
                    )
                    routines.forEach { RoutineItem(it) }
                }

                if (tasks.isNotEmpty()) {
                    if (routines.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    DetailSectionTitle(
                        title = stringResource(R.string.calendar_tasks_section),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    tasks.forEach { TaskItem(it) }
                }

                if (notes.isNotEmpty()) {
                    if (routines.isNotEmpty() || tasks.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    DetailSectionTitle(
                        title = stringResource(R.string.calendar_notes_section),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    notes.forEach { NoteItem(it) }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoutineItem(routine: Routine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = routine.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatTime(routine.startTime),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskItem(task: Task) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${statusLabel(task.status)} · ${priorityLabel(task.priority)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        task.dueDate?.let { due ->
            Text(
                text = formatTime(due),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteItem(note: Note) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = note.title,
            style = MaterialTheme.typography.bodyMedium
        )
        if (note.tags.isNotEmpty()) {
            Text(
                text = note.tags.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildMonthDays(yearMonth: YearMonth): List<LocalDate?> {
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val dayOfWeekOfFirst = firstDay.dayOfWeek.value
    val leadingDays = dayOfWeekOfFirst - 1
    val totalCells = ((leadingDays + daysInMonth + 6) / 7) * 7

    return (0 until totalCells).map { index ->
        val dayNumber = index - leadingDays + 1
        if (dayNumber in 1..daysInMonth) {
            firstDay.plusDays((dayNumber - 1).toLong())
        } else {
            null
        }
    }
}

private fun formatTime(instant: Instant): String {
    return instant.atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
}

@Composable
private fun statusLabel(status: Status): String = when (status) {
    Status.TODO -> stringResource(R.string.status_todo)
    Status.IN_PROGRESS -> stringResource(R.string.calendar_status_in_progress)
    Status.DONE -> stringResource(R.string.calendar_status_done)
}

@Composable
private fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.LOW -> stringResource(R.string.priority_low)
    Priority.MEDIUM -> stringResource(R.string.priority_medium)
    Priority.HIGH -> stringResource(R.string.priority_high)
}

private fun Routine.occursOn(date: LocalDate): Boolean {
    return startTime.atZone(ZoneId.systemDefault()).toLocalDate() == date
}

private fun Task.occursOn(date: LocalDate): Boolean {
    return dueDate?.atZone(ZoneId.systemDefault())?.toLocalDate() == date
}

private fun Note.occursOn(date: LocalDate): Boolean {
    return createdAt.atZone(ZoneId.systemDefault()).toLocalDate() == date
}
