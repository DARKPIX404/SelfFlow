package com.selfflow.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.selfflow.app.R
import com.selfflow.app.domain.model.RecurrenceRule
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.RoutineTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDialog(
    currentRoutine: Routine,
    templates: List<RoutineTemplate>,
    onDismiss: () -> Unit,
    onSaveTemplate: (RoutineTemplate) -> Unit,
    onApplyTemplate: (RoutineTemplate, LocalDate, LocalDate) -> Unit,
    onDeleteTemplate: (String) -> Unit
) {
    var templateName by remember { mutableStateOf(currentRoutine.title) }
    var selectedTemplate by remember { mutableStateOf<RoutineTemplate?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(6)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        title = { Text(stringResource(R.string.templates_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text(stringResource(R.string.template_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        val template = currentRoutine.toTemplate(templateName.trim())
                        onSaveTemplate(template)
                    },
                    enabled = templateName.trim().isNotEmpty() && currentRoutine.title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_as_template_button))
                }

                if (templates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.templates_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(templates, key = { it.id }) { template ->
                            TemplateRow(
                                template = template,
                                onApply = {
                                    selectedTemplate = it
                                    showStartDatePicker = true
                                },
                                onDelete = { onDeleteTemplate(it.id) }
                            )
                        }
                    }
                }
            }
        }
    )

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showStartDatePicker = false
                        showEndDatePicker = true
                    }
                ) {
                    Text(stringResource(R.string.next))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            endDate = if (picked.isBefore(startDate)) startDate else picked
                        }
                        selectedTemplate?.let { template ->
                            onApplyTemplate(template, startDate, endDate)
                        }
                        showEndDatePicker = false
                        selectedTemplate = null
                    }
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TemplateRow(
    template: RoutineTemplate,
    onApply: (RoutineTemplate) -> Unit,
    onDelete: (RoutineTemplate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = template.recurrenceRule.toDisplayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = { onApply(template) }) {
            Text(stringResource(R.string.apply))
        }

        IconButton(onClick = { onDelete(template) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun Routine.toTemplate(name: String): RoutineTemplate {
    val zone = ZoneId.systemDefault()
    val startLocal = LocalDateTime.ofInstant(this.startTime, zone)
    val endLocal = this.endTime?.let { LocalDateTime.ofInstant(it, zone) }
    val durationMinutes = if (endLocal != null) {
        val duration = java.time.Duration.between(startLocal, endLocal)
        maxOf(duration.toMinutes().toInt(), 1)
    } else {
        60
    }

    return RoutineTemplate(
        title = name,
        description = this.description,
        startTime = startLocal.toLocalTime(),
        durationMinutes = durationMinutes,
        recurrenceRule = this.recurrenceRule,
        notificationText = this.notificationText
    )
}

fun RoutineTemplate.generateRoutines(startDate: LocalDate, endDate: LocalDate): List<Routine> {
    val result = mutableListOf<Routine>()
    var current = startDate
    while (!current.isAfter(endDate)) {
        val shouldCreate = when (recurrenceRule) {
            RecurrenceRule.NONE -> current == startDate
            RecurrenceRule.DAILY -> true
            RecurrenceRule.WEEKDAYS -> current.dayOfWeek.value <= 5
            RecurrenceRule.WEEKLY -> current.dayOfWeek == startDate.dayOfWeek
        }

        if (shouldCreate) {
            val zone = ZoneId.systemDefault()
            val startInstant = current.atTime(startTime).atZone(zone).toInstant()
            val endInstant = current.atTime(startTime).plusMinutes(durationMinutes.toLong())
                .atZone(zone).toInstant()
            result.add(
                Routine(
                    title = title,
                    description = description,
                    startTime = startInstant,
                    endTime = endInstant,
                    recurrenceRule = RecurrenceRule.NONE,
                    notificationText = notificationText,
                    isActive = true
                )
            )
        }
        current = current.plusDays(1)
    }
    return result
}
