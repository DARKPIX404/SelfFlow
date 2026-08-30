package com.selfflow.app.domain.model

import java.time.Instant

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
