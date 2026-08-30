package com.selfflow.app.data.repository

import com.selfflow.app.data.local.dao.TaskDao
import com.selfflow.app.data.local.entity.TaskEntity
import com.selfflow.app.domain.model.Task
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
class TaskRepository constructor(
    private val taskDao: TaskDao
) {
    fun getAll(): Flow<List<Task>> =
        taskDao.getAll().map { entities -> entities.map { it.toDomain() } }

    fun getById(id: Long): Flow<Task?> =
        flow { emit(taskDao.getById(id)?.toDomain()) }

    fun getByDateRange(start: Instant, end: Instant): Flow<List<Task>> =
        taskDao.getByDateRange(start, end)
            .map { entities -> entities.map { it.toDomain() } }

    suspend fun insert(task: Task): Long =
        taskDao.insert(task.toEntity())

    suspend fun update(task: Task): Int {
        taskDao.update(task.toEntity())
        return 1
    }

    suspend fun delete(task: Task) {
        taskDao.delete(task.toEntity())
    }

    suspend fun deleteById(id: Long) {
        taskDao.deleteById(id)
    }

    private fun TaskEntity.toDomain(): Task =
        Task(
            id = id,
            title = title,
            description = description,
            status = status,
            priority = priority,
            dueDate = dueDate,
            routineId = routineId,
            createdAt = createdAt,
            completedAt = completedAt
        )

    private fun Task.toEntity(): TaskEntity =
        TaskEntity(
            id = id,
            title = title,
            description = description,
            status = status,
            priority = priority,
            dueDate = dueDate,
            routineId = routineId,
            createdAt = createdAt,
            completedAt = completedAt
        )
}
