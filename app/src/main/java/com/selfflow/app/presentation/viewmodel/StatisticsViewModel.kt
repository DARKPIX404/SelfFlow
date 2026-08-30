package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.repository.NoteRepository
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.TaskRepository
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.Status
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    taskRepository: TaskRepository,
    routineRepository: RoutineRepository,
    noteRepository: NoteRepository
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = combine(
        taskRepository.getAll(),
        routineRepository.getAll(),
        noteRepository.getAll()
    ) { tasks, routines, notes ->
        val totalTasks = tasks.size
        val doneCount = tasks.count { it.status == Status.DONE }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant()

        StatisticsUiState(
            taskCompletionRate = if (totalTasks > 0) doneCount / totalTasks.toFloat() else 0f,
            totalTasks = totalTasks,
            completedTasks = doneCount,
            statusCounts = Status.entries.associateWith { status ->
                tasks.count { it.status == status }
            },
            priorityCounts = Priority.entries.associateWith { priority ->
                tasks.count { it.priority == priority }
            },
            routinesToday = routines.count {
                it.startTime >= todayStart && it.startTime < todayEnd
            },
            routinesCompleted = routines.count {
                it.endTime != null && it.endTime < now
            },
            totalNotes = notes.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StatisticsUiState()
    )
}

data class StatisticsUiState(
    val taskCompletionRate: Float = 0f,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val statusCounts: Map<Status, Int> = emptyMap(),
    val priorityCounts: Map<Priority, Int> = emptyMap(),
    val routinesToday: Int = 0,
    val routinesCompleted: Int = 0,
    val totalNotes: Int = 0
)
