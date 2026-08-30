package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.RoutineTemplateRepository
import com.selfflow.app.domain.model.RecurrenceRule
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.RoutineTemplate
import com.selfflow.app.presentation.components.generateRoutines
import com.selfflow.app.service.alarm.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val routines: StateFlow<List<Routine>> = routineRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val templates: StateFlow<List<RoutineTemplate>> = templateRepository.templates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _dialogState = MutableStateFlow<RoutineDialogState>(RoutineDialogState.Hidden)
    val dialogState: StateFlow<RoutineDialogState> = _dialogState.asStateFlow()

    private val _templateDialogOpen = MutableStateFlow(false)
    val templateDialogOpen: StateFlow<Boolean> = _templateDialogOpen.asStateFlow()

    fun showAddDialog() {
        _dialogState.value = RoutineDialogState.Visible(
            Routine(
                title = "",
                startTime = Instant.now(),
                endTime = Instant.now().plusSeconds(3_600),
                recurrenceRule = RecurrenceRule.NONE
            )
        )
    }

    fun showEditDialog(routine: Routine) {
        _dialogState.value = RoutineDialogState.Visible(routine)
    }

    fun dismissDialog() {
        _dialogState.value = RoutineDialogState.Hidden
    }

    fun showTemplateDialog() {
        _templateDialogOpen.value = true
    }

    fun dismissTemplateDialog() {
        _templateDialogOpen.value = false
    }

    fun saveRoutine(routine: Routine) {
        viewModelScope.launch {
            val saved = if (routine.id == 0L) {
                val newId = routineRepository.insert(routine)
                routine.copy(id = newId)
            } else {
                routineRepository.update(routine)
                routine
            }
            updateAlarm(saved)
            dismissDialog()
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            routineRepository.delete(routine)
            alarmScheduler.cancelRoutineAlarm(routine.id)
        }
    }

    fun saveTemplate(template: RoutineTemplate) {
        viewModelScope.launch {
            templateRepository.saveTemplate(template)
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            templateRepository.deleteTemplate(templateId)
        }
    }

    fun applyTemplate(template: RoutineTemplate, startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            val routinesToCreate = template.generateRoutines(startDate, endDate)
            routinesToCreate.forEach { routine ->
                val newId = routineRepository.insert(routine)
                alarmScheduler.scheduleRoutineAlarm(routine.copy(id = newId))
            }
            _templateDialogOpen.value = false
        }
    }

    private fun updateAlarm(routine: Routine) {
        if (routine.isActive) {
            alarmScheduler.scheduleRoutineAlarm(routine)
        } else {
            alarmScheduler.cancelRoutineAlarm(routine.id)
        }
    }

    sealed class RoutineDialogState {
        data object Hidden : RoutineDialogState()
        data class Visible(val routine: Routine) : RoutineDialogState()
    }
}
