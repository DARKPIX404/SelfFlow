package com.selfflow.app.presentation.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.R
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.Status
import com.selfflow.app.presentation.components.charts.TaskPriorityBarChart
import com.selfflow.app.presentation.components.charts.TaskStatusPieChart
import com.selfflow.app.presentation.viewmodel.StatisticsUiState
import com.selfflow.app.presentation.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.statistics_title)) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CompletionRateCard(
                rate = uiState.taskCompletionRate,
                completed = uiState.completedTasks,
                total = uiState.totalTasks
            )
            StatusDistributionCard(counts = uiState.statusCounts)
            PriorityDistributionCard(counts = uiState.priorityCounts)
            StatusChartCard(counts = uiState.statusCounts)
            PriorityChartCard(counts = uiState.priorityCounts)
            RoutineStatsCard(
                todayCount = uiState.routinesToday,
                completedCount = uiState.routinesCompleted
            )
            NotesCountCard(count = uiState.totalNotes)
        }
    }
}

@Composable
private fun CompletionRateCard(rate: Float, completed: Int, total: Int) {
    StatCard(title = stringResource(R.string.stats_task_completion_title)) {
        Column {
            Text(
                text = "${(rate * 100).toInt()}%",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.stats_completed_out_of, completed, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = rate,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusDistributionCard(counts: Map<Status, Int>) {
    val items = listOf(
        DistributionItem(stringResource(R.string.status_todo), counts[Status.TODO] ?: 0, MaterialTheme.colorScheme.outline),
        DistributionItem(stringResource(R.string.status_in_progress), counts[Status.IN_PROGRESS] ?: 0, MaterialTheme.colorScheme.primary),
        DistributionItem(stringResource(R.string.status_done), counts[Status.DONE] ?: 0, MaterialTheme.colorScheme.tertiary)
    )
    DistributionCard(title = stringResource(R.string.stats_status_distribution_title), items = items)
}

@Composable
private fun PriorityDistributionCard(counts: Map<Priority, Int>) {
    val items = listOf(
        DistributionItem(stringResource(R.string.priority_low), counts[Priority.LOW] ?: 0, MaterialTheme.colorScheme.secondary),
        DistributionItem(stringResource(R.string.priority_medium), counts[Priority.MEDIUM] ?: 0, MaterialTheme.colorScheme.primary),
        DistributionItem(stringResource(R.string.priority_high), counts[Priority.HIGH] ?: 0, MaterialTheme.colorScheme.error)
    )
    DistributionCard(title = stringResource(R.string.stats_priority_distribution_title), items = items)
}

@Composable
private fun RoutineStatsCard(todayCount: Int, completedCount: Int) {
    StatCard(title = stringResource(R.string.stats_routines_title)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatValue(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.stats_routines_today),
                value = todayCount.toString()
            )
            StatValue(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.stats_routines_completed),
                value = completedCount.toString()
            )
        }
    }
}

@Composable
private fun StatusChartCard(counts: Map<Status, Int>) {
    val labels = mapOf(
        Status.TODO to stringResource(R.string.status_todo),
        Status.IN_PROGRESS to stringResource(R.string.status_in_progress),
        Status.DONE to stringResource(R.string.status_done)
    )
    StatCard(title = stringResource(R.string.stats_status_chart_title)) {
        TaskStatusPieChart(
            counts = counts,
            labels = labels,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )
    }
}

@Composable
private fun PriorityChartCard(counts: Map<Priority, Int>) {
    val labels = mapOf(
        Priority.LOW to stringResource(R.string.priority_low),
        Priority.MEDIUM to stringResource(R.string.priority_medium),
        Priority.HIGH to stringResource(R.string.priority_high)
    )
    StatCard(title = stringResource(R.string.stats_priority_chart_title)) {
        TaskPriorityBarChart(
            counts = counts,
            labels = labels,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )
    }
}

@Composable
private fun NotesCountCard(count: Int) {
    StatCard(title = stringResource(R.string.stats_notes_title)) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DistributionCard(title: String, items: List<DistributionItem>) {
    StatCard(title = title) {
        val total = items.sumOf { it.count }
        if (total > 0) {
            StackedBar(items = items)
            Spacer(modifier = Modifier.height(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { item ->
                LegendRow(item = item)
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StackedBar(items: List<DistributionItem>) {
    val total = items.sumOf { it.count }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        items.forEach { item ->
            if (item.count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(item.count / total.toFloat())
                        .background(item.color)
                )
            }
        }
    }
}

@Composable
private fun LegendRow(item: DistributionItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(item.color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = item.count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatValue(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class DistributionItem(
    val label: String,
    val count: Int,
    val color: Color
)
