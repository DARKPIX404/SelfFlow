package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Status
import com.selfflow.app.domain.model.Task
import com.selfflow.app.domain.usecase.GetTodayScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val nextRoutine: Routine? = null,
    val tasks: List<Task> = emptyList(),
    val completedTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    getTodayScheduleUseCase: GetTodayScheduleUseCase
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = getTodayScheduleUseCase(today = LocalDate.now())
        .map { schedule ->
            val now = Instant.now()
            val nextRoutine = schedule.routines
                .filter { it.startTime.isAfter(now) }
                .minByOrNull { it.startTime }

            val totalTasksCount = schedule.tasks.size
            val completedTasksCount = schedule.tasks.count { it.status == Status.DONE }
            val incompleteTasks = schedule.tasks
                .filter { it.status != Status.DONE }
                .sortedByDescending { it.priority.ordinal }
                .take(MAX_VISIBLE_TASKS)

            HomeUiState(
                nextRoutine = nextRoutine,
                tasks = incompleteTasks,
                completedTasksCount = completedTasksCount,
                totalTasksCount = totalTasksCount,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = HomeUiState(isLoading = true)
        )

    companion object {
        private const val MAX_VISIBLE_TASKS = 3
    }
}
