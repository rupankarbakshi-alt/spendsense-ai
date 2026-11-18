package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CurrencyOption
import com.example.ml.CategoryBreakdownItem
import com.example.ml.MonthlyPoint
import com.example.ui.theme.MonoFinancialStyle
import kotlin.math.max

@Composable
fun CategoryDonutChart(
    breakdown: List<CategoryBreakdownItem>,
    currency: CurrencyOption,
    totalSpend: Double,
    modifier: Modifier = Modifier
) {
    if (breakdown.isEmpty() || totalSpend <= 0.0) return

    var selectedIndex by remember { mutableIntStateOf(0) }
    var animateTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(breakdown) {
        animateTrigger = true
        if (selectedIndex >= breakdown.size) selectedIndex = 0
    }
    val sweepProgress by animateFloatAsState(
        targetValue = if (animateTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "donut_sweep"
    )

    val selected = breakdown.getOrNull(selectedIndex) ?: breakdown.first()
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(154.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(144.dp)) {
                    val strokeWidth = 22.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )

                    var startAngle = -90f
                    breakdown.forEachIndexed { index, item ->
                        val fraction = (item.amount / totalSpend).toFloat().coerceIn(0f, 1f)
                        val sweep = fraction * 360f * sweepProgress
                        val isSelected = index == selectedIndex
                        val activeStroke = if (isSelected) strokeWidth * 1.18f else strokeWidth
                        drawArc(
                            color = item.category.chartColor,
                            startAngle = startAngle,
                            sweepAngle = (sweep - 2.2f).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = activeStroke, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${selected.percentageOfTotal.toInt()}%",
                        style = MaterialTheme.typography.titleLarge.merge(MonoFinancialStyle),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selected.category.displayName.substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                breakdown.take(5).forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable { selectedIndex = index }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(item.category.chartColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.category.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = currency.format(item.amount),
                            style = MaterialTheme.typography.labelSmall.merge(MonoFinancialStyle),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingTrendLineChart(
    dailySeries: List<Pair<Int, Double>>,
    currency: CurrencyOption,
    modifier: Modifier = Modifier
) {
    if (dailySeries.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val dotHighlight = MaterialTheme.colorScheme.secondary

    val maxVal = max(dailySeries.maxOfOrNull { it.second } ?: 1.0, 1.0)
    val peakDay = dailySeries.maxByOrNull { it.second }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daily Spend Trajectory (Day 1–${dailySeries.last().first})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (peakDay != null && peakDay.second > 0.0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Peak Day ${peakDay.first}: ${currency.format(peakDay.second)}",
                        style = MaterialTheme.typography.labelSmall.merge(MonoFinancialStyle),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val leftPad = 8.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 10.dp.toPx()
            val bottomPad = 14.dp.toPx()

            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            // Horizontal reference lines
            for (step in 0..2) {
                val y = topPad + (chartH / 2f) * step
                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + chartW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }

            val points = dailySeries.mapIndexed { idx, (_, value) ->
                val xRatio = if (dailySeries.size <= 1) 0.5f else idx.toFloat() / (dailySeries.size - 1)
                val yRatio = (value / maxVal).toFloat().coerceIn(0f, 1f)
                Offset(
                    x = leftPad + xRatio * chartW,
                    y = topPad + chartH * (1f - yRatio)
                )
            }

            if (points.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(points.last().x, topPad + chartH)
                    lineTo(points.first().x, topPad + chartH)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.30f),
                            lineColor.copy(alpha = 0.02f)
                        ),
                        startY = topPad,
                        endY = topPad + chartH
                    )
                )

                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            points.forEachIndexed { idx, pt ->
                if (dailySeries[idx].second > 0.0) {
                    val isPeak = dailySeries[idx] == peakDay
                    drawCircle(
                        color = if (isPeak) dotHighlight else lineColor,
                        radius = if (isPeak) 5.dp.toPx() else 3.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyForecastBarChart(
    series: List<MonthlyPoint>,
    budgetTarget: Double,
    currency: CurrencyOption,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) return

    val actualColor = MaterialTheme.colorScheme.secondary
    val forecastColor = MaterialTheme.colorScheme.primary
    val targetLineColor = MaterialTheme.colorScheme.tertiary
    val maxVal = max(
        max(series.maxOfOrNull { it.actualTotal } ?: 1.0, budgetTarget),
        1.0
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val topPad = 14.dp.toPx()
            val bottomPad = 10.dp.toPx()
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
            val count = series.size.coerceAtLeast(1)
            val slotW = size.width / count
            val barW = (slotW * 0.52f).coerceAtMost(38.dp.toPx())

            // Budget target dashed line
            if (budgetTarget > 0.0) {
                val targetY = topPad + chartH * (1f - (budgetTarget / maxVal).toFloat().coerceIn(0f, 1f))
                drawLine(
                    color = targetLineColor,
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            }

            series.forEachIndexed { index, point ->
                val ratio = (point.actualTotal / maxVal).toFloat().coerceIn(0.03f, 1f)
                val barH = chartH * ratio
                val x = index * slotW + (slotW - barW) / 2f
                val y = topPad + chartH - barH

                drawRoundRect(
                    color = if (point.isForecast) forecastColor else actualColor.copy(alpha = 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            series.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (pt.isForecast) FontWeight.Bold else FontWeight.Normal,
                        color = if (pt.isForecast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = currency.format(pt.actualTotal),
                        style = MaterialTheme.typography.labelSmall.merge(MonoFinancialStyle),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
