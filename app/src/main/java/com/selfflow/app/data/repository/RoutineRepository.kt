package com.selfflow.app.data.repository

import com.selfflow.app.data.local.dao.RoutineDao
import com.selfflow.app.data.local.entity.RoutineEntity
import com.selfflow.app.domain.model.Routine
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
class RoutineRepository constructor(
    private val routineDao: RoutineDao
) {
    fun getAll(): Flow<List<Routine>> =
        routineDao.getAll().map { entities -> entities.map { it.toDomain() } }

    fun getById(id: Long): Flow<Routine?> =
        flow { emit(routineDao.getById(id)?.toDomain()) }

    fun getByDateRange(start: Instant, end: Instant): Flow<List<Routine>> =
        routineDao.getByDateRange(start, end)
            .map { entities -> entities.map { it.toDomain() } }

    fun getActiveRoutines(): Flow<List<Routine>> =
        routineDao.getActive().map { entities -> entities.map { it.toDomain() } }

    suspend fun insert(routine: Routine): Long =
        routineDao.insert(routine.toEntity())

    suspend fun update(routine: Routine): Int {
        routineDao.update(routine.toEntity())
        return 1
    }

    suspend fun delete(routine: Routine) {
        routineDao.delete(routine.toEntity())
    }

    suspend fun deleteById(id: Long) {
        routineDao.deleteById(id)
    }

    private fun RoutineEntity.toDomain(): Routine =
        Routine(
            id = id,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            recurrenceRule = recurrenceRule,
            category = category,
            notificationText = notificationText,
            isActive = isActive
        )

    private fun Routine.toEntity(): RoutineEntity =
        RoutineEntity(
            id = id,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            recurrenceRule = recurrenceRule,
            category = category,
            notificationText = notificationText,
            isActive = isActive
        )
}
