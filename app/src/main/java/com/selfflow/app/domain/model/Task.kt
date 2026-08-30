package com.selfflow.app.domain.model

import java.time.Instant

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val status: Status = Status.TODO,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: Instant? = null,
    val routineId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)
