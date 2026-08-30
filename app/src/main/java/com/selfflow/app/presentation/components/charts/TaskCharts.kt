package com.selfflow.app.presentation.components.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.selfflow.app.domain.model.Priority
import com.selfflow.app.domain.model.Status

@Composable
fun TaskStatusPieChart(
    counts: Map<Status, Int>,
    labels: Map<Status, String>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                isDrawHoleEnabled = true
                setUsePercentValues(true)
                legend.isEnabled = true
                setEntryLabelTextSize(12f)
                setEntryLabelColor(android.graphics.Color.BLACK)
            }
        },
        update = { chart ->
            val entries = Status.entries.mapNotNull { status ->
                val count = counts[status] ?: 0
                if (count > 0) PieEntry(count.toFloat(), labels[status] ?: status.name) else null
            }
            if (entries.isEmpty()) {
                chart.clear()
            } else {
                val dataSet = PieDataSet(entries, "").apply {
                    colors = ColorTemplate.MATERIAL_COLORS.toList()
                    valueTextSize = 12f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String = "%.0f%%".format(value)
                    }
                }
                chart.data = PieData(dataSet)
            }
            chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
fun TaskPriorityBarChart(
    counts: Map<Priority, Int>,
    labels: Map<Priority, String>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                axisLeft.setDrawGridLines(false)
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = Priority.entries.mapIndexed { index, priority ->
                BarEntry(index.toFloat(), (counts[priority] ?: 0).toFloat())
            }
            val dataSet = BarDataSet(entries, "").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueTextSize = 12f
            }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    labels[Priority.entries.getOrNull(value.toInt())] ?: ""
            }
            chart.data = BarData(dataSet)
            chart.data.barWidth = 0.5f
            chart.invalidate()
        },
        modifier = modifier
    )
}
