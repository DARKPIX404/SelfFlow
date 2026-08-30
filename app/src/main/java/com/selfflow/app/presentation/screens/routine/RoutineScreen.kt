package com.selfflow.app.presentation.screens.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.R
import com.selfflow.app.domain.model.RecurrenceRule
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.presentation.components.EmptyState
import com.selfflow.app.presentation.components.toDisplayName
import com.selfflow.app.presentation.components.RoutineFormDialog
import com.selfflow.app.presentation.components.TemplateDialog
import com.selfflow.app.presentation.viewmodel.RoutineViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    viewModel: RoutineViewModel = hiltViewModel()
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val templateDialogOpen by viewModel.templateDialogOpen.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routine_screen_title)) },
                actions = {
                    IconButton(onClick = viewModel::showTemplateDialog) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = stringResource(R.string.templates_title)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddDialog) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_routine_content_description)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (routines.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Schedule,
                    title = stringResource(R.string.routine_empty_title),
                    description = stringResource(R.string.routine_empty_state),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = routines,
                        key = { it.id }
                    ) { routine ->
                        RoutineListItem(
                            routine = routine,
                            onClick = { viewModel.showEditDialog(routine) },
                            onDelete = { viewModel.deleteRoutine(routine) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }

    when (val state = dialogState) {
        is RoutineViewModel.RoutineDialogState.Visible -> {
            RoutineFormDialog(
                routine = state.routine,
                onDismiss = viewModel::dismissDialog,
                onSave = viewModel::saveRoutine
            )
        }

        else -> Unit
    }

    if (templateDialogOpen) {
        val currentRoutine = (dialogState as? RoutineViewModel.RoutineDialogState.Visible)?.routine
            ?: Routine(
                title = "",
                startTime = Instant.now(),
                endTime = Instant.now().plusSeconds(3_600),
                recurrenceRule = RecurrenceRule.NONE
            )

        TemplateDialog(
            currentRoutine = currentRoutine,
            templates = templates,
            onDismiss = viewModel::dismissTemplateDialog,
            onSaveTemplate = viewModel::saveTemplate,
            onApplyTemplate = viewModel::applyTemplate,
            onDeleteTemplate = viewModel::deleteTemplate
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineListItem(
    routine: Routine,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    RoutineCard(
        routine = routine,
        onClick = onClick,
        onDelete = onDelete,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineCard(
    routine: Routine,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val start = LocalDateTime.ofInstant(routine.startTime, ZoneId.systemDefault())
        .format(timeFormatter)
    val end = routine.endTime?.let {
        LocalDateTime.ofInstant(it, ZoneId.systemDefault()).format(timeFormatter)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RowInfo(
                    title = routine.title,
                    badge = if (routine.isActive) {
                        stringResource(R.string.routine_active)
                    } else {
                        stringResource(R.string.routine_inactive)
                    }
                )

                if (routine.description.isNotBlank()) {
                    Text(
                        text = routine.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = if (end != null) "$start — $end" else start,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (routine.recurrenceRule != RecurrenceRule.NONE) {
                    Text(
                        text = stringResource(
                            R.string.routine_recurrence_display,
                            routine.recurrenceRule.toDisplayName()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun RowInfo(
    title: String,
    badge: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
