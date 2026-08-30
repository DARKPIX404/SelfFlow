package com.selfflow.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFormDialog(
    routine: Routine,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit
) {
    var title by remember(routine.id) { mutableStateOf(routine.title) }
    var description by remember(routine.id) { mutableStateOf(routine.description) }
    var recurrenceRule by remember(routine.id) { mutableStateOf(routine.recurrenceRule) }
    var notificationText by remember(routine.id) { mutableStateOf(routine.notificationText ?: "") }
    var isActive by remember(routine.id) { mutableStateOf(routine.isActive) }
    var startTime by remember(routine.id) { mutableStateOf(routine.startTime.toLocalTime()) }
    var endTime by remember(routine.id) { mutableStateOf(routine.endTime?.toLocalTime()) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (routine.id == 0L) {
                    stringResource(R.string.routine_dialog_title_new)
                } else {
                    stringResource(R.string.routine_dialog_title_edit)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = routine.copy(
                        title = title.trim(),
                        description = description.trim(),
                        startTime = startTime.toInstantOnDate(routine.startTime),
                        endTime = endTime?.toInstantOnDate(routine.endTime ?: routine.startTime),
                        recurrenceRule = recurrenceRule,
                        notificationText = notificationText.trim().takeIf { it.isNotEmpty() },
                        isActive = isActive
                    )
                    onSave(updated)
                },
                enabled = title.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.routine_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.routine_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(
                                R.string.routine_start_time_button,
                                startTime.format(timeFormatter)
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(
                                R.string.routine_end_time_button,
                                endTime?.format(timeFormatter) ?: "—"
                            )
                        )
                    }
                }

                RecurrenceDropdown(
                    selected = recurrenceRule,
                    onSelected = { recurrenceRule = it }
                )

                OutlinedTextField(
                    value = notificationText,
                    onValueChange = { notificationText = it },
                    label = { Text(stringResource(R.string.routine_notification_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.routine_active_label))
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }
            }
        }
    )

    if (showStartPicker) {
        TimePickerDialog(
            initialTime = startTime,
            onConfirm = {
                startTime = it
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        TimePickerDialog(
            initialTime = endTime ?: LocalTime.now(),
            onConfirm = {
                endTime = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceDropdown(
    selected: RecurrenceRule,
    onSelected: (RecurrenceRule) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.toDisplayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.routine_recurrence_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RecurrenceRule.entries.forEach { rule ->
                DropdownMenuItem(
                    text = { Text(rule.toDisplayName()) },
                    onClick = {
                        onSelected(rule)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = { TimePicker(state = state) }
    )
}

@Composable
fun RecurrenceRule.toDisplayName(): String = when (this) {
    RecurrenceRule.NONE -> stringResource(R.string.recurrence_none)
    RecurrenceRule.DAILY -> stringResource(R.string.recurrence_daily)
    RecurrenceRule.WEEKDAYS -> stringResource(R.string.recurrence_weekdays)
    RecurrenceRule.WEEKLY -> stringResource(R.string.recurrence_weekly)
}

private fun Instant.toLocalTime(): LocalTime =
    LocalDateTime.ofInstant(this, ZoneId.systemDefault()).toLocalTime()

private fun LocalTime.toInstantOnDate(base: Instant): Instant {
    val zone = ZoneId.systemDefault()
    val date = LocalDateTime.ofInstant(base, zone).toLocalDate()
    return ZonedDateTime.of(date, this, zone).toInstant()
}
