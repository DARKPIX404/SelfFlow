package com.selfflow.app.domain.model

import java.time.Instant

data class Routine(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val startTime: Instant,
    val endTime: Instant? = null,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val category: String = "",
    val notificationText: String? = null,
    val isActive: Boolean = true
)
