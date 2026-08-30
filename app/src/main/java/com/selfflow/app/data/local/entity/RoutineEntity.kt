package com.selfflow.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.selfflow.app.domain.model.RecurrenceRule
import java.time.Instant

@Entity(
    tableName = "routines",
    indices = [
        Index(value = ["start_time"]),
        Index(value = ["is_active"])
    ]
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "start_time")
    val startTime: Instant,

    @ColumnInfo(name = "end_time")
    val endTime: Instant? = null,

    @ColumnInfo(name = "recurrence_rule")
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,

    @ColumnInfo(name = "category")
    val category: String = "",

    @ColumnInfo(name = "notification_text")
    val notificationText: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)
