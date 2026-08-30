package com.selfflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.selfflow.app.data.local.converter.Converters
import com.selfflow.app.data.local.dao.NoteDao
import com.selfflow.app.data.local.dao.RoutineDao
import com.selfflow.app.data.local.dao.TaskDao
import com.selfflow.app.data.local.entity.NoteEntity
import com.selfflow.app.data.local.entity.RoutineEntity
import com.selfflow.app.data.local.entity.TaskEntity

@Database(
    entities = [RoutineEntity::class, TaskEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
}
