package com.selfflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.selfflow.app.data.local.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY start_time ASC")
    fun getAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: Long): RoutineEntity?

    @Query("SELECT * FROM routines WHERE start_time >= :start AND start_time < :end ORDER BY start_time ASC")
    fun getByDateRange(start: Instant, end: Instant): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE is_active = 1 ORDER BY start_time ASC")
    fun getActive(): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Long)
}
