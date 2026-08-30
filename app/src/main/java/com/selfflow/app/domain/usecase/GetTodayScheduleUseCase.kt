package com.selfflow.app.domain.usecase

import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.TaskRepository
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Task
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class TodaySchedule(
    val routines: List<Routine>,
    val tasks: List<Task>
)

class GetTodayScheduleUseCase @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository
) {
    operator fun invoke(today: LocalDate = LocalDate.now()): Flow<TodaySchedule> =
        combine(
            routineRepository.getAll(),
            taskRepository.getAll()
        ) { routines, tasks ->
            val zone = ZoneId.systemDefault()
            val start = today.atStartOfDay(zone).toInstant()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant()

            TodaySchedule(
                routines = routines.filter { it.startTime >= start && it.startTime < end },
                tasks = tasks.filter { task ->
                    task.dueDate?.let { due -> due >= start && due < end } ?: false
                }
            )
        }
}
