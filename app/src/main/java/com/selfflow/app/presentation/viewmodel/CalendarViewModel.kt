package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.repository.NoteRepository
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.TaskRepository
import com.selfflow.app.domain.model.Note
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val routines: List<Routine> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                routineRepository.getAll(),
                taskRepository.getAll(),
                noteRepository.getAll()
            ) { routines, tasks, notes ->
                Triple(routines, tasks, notes)
            }.collect { (routines, tasks, notes) ->
                _uiState.update {
                    it.copy(
                        routines = routines,
                        tasks = tasks,
                        notes = notes,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onPreviousMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun onNextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onClearSelection() {
        _uiState.update { it.copy(selectedDate = null) }
    }

    fun routinesForDate(date: LocalDate): List<Routine> =
        uiState.value.routines.filter { it.occursOn(date) }

    fun tasksForDate(date: LocalDate): List<Task> =
        uiState.value.tasks.filter { it.occursOn(date) }

    fun notesForDate(date: LocalDate): List<Note> =
        uiState.value.notes.filter { it.occursOn(date) }
}

private fun Routine.occursOn(date: LocalDate): Boolean {
    val zone = ZoneId.systemDefault()
    return startTime.atZone(zone).toLocalDate() == date
}

private fun Task.occursOn(date: LocalDate): Boolean {
    val zone = ZoneId.systemDefault()
    return dueDate?.atZone(zone)?.toLocalDate() == date
}

private fun Note.occursOn(date: LocalDate): Boolean {
    val zone = ZoneId.systemDefault()
    return createdAt.atZone(zone).toLocalDate() == date
}
