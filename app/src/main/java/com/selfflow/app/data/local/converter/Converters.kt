package com.selfflow.app.data.local.converter

import androidx.room.TypeConverter
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.RecurrenceRule
import com.selfflow.app.domain.model.Status
import java.time.Instant
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun fromStatus(value: Status?): String? = value?.name

    @TypeConverter
    fun toStatus(value: String?): Status? = value?.let { Status.valueOf(it) }

    @TypeConverter
    fun fromPriority(value: Priority?): String? = value?.name

    @TypeConverter
    fun toPriority(value: String?): Priority? = value?.let { Priority.valueOf(it) }

    @TypeConverter
    fun fromRecurrenceRule(value: RecurrenceRule?): String? = value?.name

    @TypeConverter
    fun toRecurrenceRule(value: String?): RecurrenceRule = value?.let {
        RecurrenceRule.entries.find { rule -> rule.name == it }
    } ?: RecurrenceRule.NONE

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.let {
        if (it.isEmpty()) emptyList() else it.split(",")
    }
}
