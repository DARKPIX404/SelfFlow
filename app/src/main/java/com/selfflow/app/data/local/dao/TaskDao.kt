package com.selfflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.selfflow.app.data.local.entity.TaskEntity
import com.selfflow.app.domain.model.Status
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY due_date ASC, created_at ASC")
    fun getAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE due_date >= :start AND due_date < :end ORDER BY due_date ASC")
    fun getByDateRange(start: Instant, end: Instant): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY due_date ASC")
    fun getByStatus(status: Status): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE routine_id = :routineId ORDER BY due_date ASC")
    fun getByRoutineId(routineId: Long): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
