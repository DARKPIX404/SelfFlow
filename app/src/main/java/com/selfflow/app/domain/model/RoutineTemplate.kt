package com.selfflow.app.domain.model

import java.time.LocalTime
import java.util.UUID

data class RoutineTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val startTime: LocalTime,
    val durationMinutes: Int = 60,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val notificationText: String? = null
)
