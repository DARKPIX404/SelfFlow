package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.TaskRepository
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Status
import com.selfflow.app.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _selectedStatus = MutableStateFlow<Status?>(null)

    private val _tasks = taskRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _routines = routineRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<TasksUiState> = combine(
        _tasks,
        _routines,
        _selectedStatus
    ) { tasks, routines, selectedStatus ->
        val filtered = selectedStatus?.let { status ->
            tasks.filter { it.status == status }
        } ?: tasks

        TasksUiState(
            tasks = filtered.sortedWith(compareBy({ it.dueDate == null }, { it.dueDate })),
            routines = routines,
            selectedStatus = selectedStatus,
            taskCounts = Status.entries.associateWith { status -> tasks.count { it.status == status } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    private val _dialogState = MutableStateFlow<TaskDialogState>(TaskDialogState.Closed)
    val dialogState: StateFlow<TaskDialogState> = _dialogState

    fun selectStatusFilter(status: Status?) {
        _selectedStatus.value = status
    }

    fun openAddDialog() {
        _dialogState.value = TaskDialogState.Open(task = null)
    }

    fun openEditDialog(task: Task) {
        _dialogState.value = TaskDialogState.Open(task = task)
    }

    fun closeDialog() {
        _dialogState.value = TaskDialogState.Closed
    }

    fun saveTask(
        title: String,
        description: String,
        dueDate: Instant?,
        priority: Priority,
        status: Status,
        routineId: Long?
    ) {
        val currentDialog = _dialogState.value as? TaskDialogState.Open ?: return
        val task = currentDialog.task

        viewModelScope.launch {
            val updated = task?.copy(
                title = title,
                description = description,
                dueDate = dueDate,
                priority = priority,
                status = status,
                routineId = routineId,
                completedAt = if (status == Status.DONE) task.completedAt ?: Instant.now() else null
            ) ?: Task(
                title = title,
                description = description,
                dueDate = dueDate,
                priority = priority,
                status = status,
                routineId = routineId,
                completedAt = if (status == Status.DONE) Instant.now() else null
            )

            if (task == null) {
                taskRepository.insert(updated)
            } else {
                taskRepository.update(updated)
            }
            closeDialog()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.delete(task)
        }
    }

    fun toggleStatus(task: Task) {
        val nextStatus = when (task.status) {
            Status.TODO -> Status.IN_PROGRESS
            Status.IN_PROGRESS -> Status.DONE
            Status.DONE -> Status.TODO
        }
        val completedAt = if (nextStatus == Status.DONE) Instant.now() else null
        viewModelScope.launch {
            taskRepository.update(task.copy(status = nextStatus, completedAt = completedAt))
        }
    }
}

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val selectedStatus: Status? = null,
    val taskCounts: Map<Status, Int> = emptyMap()
)

sealed class TaskDialogState {
    data object Closed : TaskDialogState()
    data class Open(val task: Task?) : TaskDialogState()
}
