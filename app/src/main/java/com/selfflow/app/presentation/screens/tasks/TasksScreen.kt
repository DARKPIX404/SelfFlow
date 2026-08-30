package com.selfflow.app.presentation.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.R
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Status
import com.selfflow.app.domain.model.Task
import com.selfflow.app.presentation.viewmodel.TaskDialogState
import com.selfflow.app.presentation.viewmodel.TasksViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tasks_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddDialog) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task_content_description))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            StatusFilterChips(
                selectedStatus = uiState.selectedStatus,
                counts = uiState.taskCounts,
                onSelected = viewModel::selectStatusFilter
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(uiState.tasks, key = { it.id }) { task ->
                    TaskListItem(
                        task = task,
                        routine = uiState.routines.find { it.id == task.routineId },
                        onClick = { viewModel.openEditDialog(task) },
                        onToggleStatus = { viewModel.toggleStatus(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }

    val openDialog = dialogState as? TaskDialogState.Open
    if (openDialog != null) {
        TaskFormDialog(
            task = openDialog.task,
            routines = uiState.routines,
            onDismiss = viewModel::closeDialog,
            onSave = { title, description, dueDate, priority, status, routineId ->
                viewModel.saveTask(title, description, dueDate, priority, status, routineId)
            }
        )
    }
}

@Composable
private fun StatusFilterChips(
    selectedStatus: Status?,
    counts: Map<Status, Int>,
    onSelected: (Status?) -> Unit
) {
    val options = listOf(
        null to stringResource(R.string.task_status_filter_all),
        Status.TODO to stringResource(R.string.status_todo),
        Status.IN_PROGRESS to stringResource(R.string.status_in_progress),
        Status.DONE to stringResource(R.string.status_done)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (status, label) ->
            val count = status?.let { counts[it] ?: 0 } ?: counts.values.sum()
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onSelected(status) },
                label = { Text("$label ($count)") }
            )
        }
    }
}

@Composable
private fun TaskListItem(
    task: Task,
    routine: Routine?,
    onClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(status = task.status, onClick = onToggleStatus)
                    PriorityLabel(priority = task.priority)
                    task.dueDate?.let { due ->
                        Text(
                            text = due.formatDue(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (routine != null) {
                    Text(
                        text = stringResource(R.string.task_routine_prefix, routine.title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: Status, onClick: () -> Unit) {
    val (label, color) = when (status) {
        Status.TODO -> stringResource(R.string.status_todo) to MaterialTheme.colorScheme.outline
        Status.IN_PROGRESS -> stringResource(R.string.status_in_progress) to MaterialTheme.colorScheme.primary
        Status.DONE -> stringResource(R.string.status_done) to MaterialTheme.colorScheme.tertiary
    }
    FilterChip(
        selected = status != Status.TODO,
        onClick = onClick,
        label = { Text(label) },
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = status != Status.TODO,
            borderColor = color
        )
    )
}

@Composable
private fun PriorityLabel(priority: Priority) {
    val (label, color) = when (priority) {
        Priority.LOW -> stringResource(R.string.priority_low) to MaterialTheme.colorScheme.secondary
        Priority.MEDIUM -> stringResource(R.string.priority_medium) to MaterialTheme.colorScheme.primary
        Priority.HIGH -> stringResource(R.string.priority_high) to MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormDialog(
    task: Task?,
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onSave: (String, String, Instant?, Priority, Status, Long?) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(task?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(task?.description ?: "") }
    var dueDate by remember {
        mutableStateOf(task?.dueDate?.toLocalDateTime() ?: LocalDateTime.now())
    }
    var priority by rememberSaveable { mutableStateOf(task?.priority ?: Priority.MEDIUM) }
    var status by rememberSaveable { mutableStateOf(task?.status ?: Status.TODO) }
    var routineId by rememberSaveable { mutableStateOf(task?.routineId) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (task == null) {
                    stringResource(R.string.task_dialog_title_new)
                } else {
                    stringResource(R.string.task_dialog_title_edit)
                }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dueDate.formatDate())
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dueDate.formatTime())
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                PriorityDropdown(
                    selected = priority,
                    onSelected = { priority = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusDropdown(
                    selected = status,
                    onSelected = { status = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                RoutineDropdown(
                    routines = routines,
                    selectedId = routineId,
                    onSelected = { routineId = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title,
                        description,
                        dueDate.toInstant(ZoneId.systemDefault()),
                        priority,
                        status,
                        routineId
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val local = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            dueDate = LocalDateTime.of(local, dueDate.toLocalTime())
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dueDate.hour,
            initialMinute = dueDate.minute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.task_due_date_dialog_title)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dueDate = dueDate.withHour(timePickerState.hour)
                            .withMinute(timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityDropdown(
    selected: Priority,
    onSelected: (Priority) -> Unit
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
            label = { Text(stringResource(R.string.task_priority_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Priority.entries.forEach { priority ->
                DropdownMenuItem(
                    text = { Text(priority.toDisplayName()) },
                    onClick = {
                        onSelected(priority)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(
    selected: Status,
    onSelected: (Status) -> Unit
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
            label = { Text(stringResource(R.string.task_status_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Status.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.toDisplayName()) },
                    onClick = {
                        onSelected(status)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineDropdown(
    routines: List<Routine>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = routines.find { it.id == selectedId }?.title
        ?: stringResource(R.string.task_no_routine)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.task_routine_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_no_routine)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            routines.forEach { routine ->
                DropdownMenuItem(
                    text = { Text(routine.title) },
                    onClick = {
                        onSelected(routine.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun Instant.toLocalDateTime(): LocalDateTime =
    atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun LocalDateTime.toInstant(zone: ZoneId): Instant =
    atZone(zone).toInstant()

private fun LocalDateTime.formatDate(): String =
    format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ru")))

private fun LocalDateTime.formatTime(): String =
    format(DateTimeFormatter.ofPattern("HH:mm", Locale("ru")))

private fun Instant.formatDue(): String =
    atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale("ru")))

@Composable
private fun Priority.toDisplayName(): String = when (this) {
    Priority.LOW -> stringResource(R.string.priority_low)
    Priority.MEDIUM -> stringResource(R.string.priority_medium)
    Priority.HIGH -> stringResource(R.string.priority_high)
}

@Composable
private fun Status.toDisplayName(): String = when (this) {
    Status.TODO -> stringResource(R.string.status_todo)
    Status.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    Status.DONE -> stringResource(R.string.status_done)
}
