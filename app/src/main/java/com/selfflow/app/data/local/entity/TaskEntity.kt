package com.selfflow.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.Status
import java.time.Instant

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["due_date"]),
        Index(value = ["status"]),
        Index(value = ["routine_id"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "status")
    val status: Status = Status.TODO,

    @ColumnInfo(name = "priority")
    val priority: Priority = Priority.MEDIUM,

    @ColumnInfo(name = "due_date")
    val dueDate: Instant? = null,

    @ColumnInfo(name = "routine_id")
    val routineId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Instant? = null
)
