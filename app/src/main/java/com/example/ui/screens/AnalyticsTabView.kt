package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.ModelLimitationBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.SpendingTrendLineChart
import com.example.ui.components.StatusChip
import com.example.ui.theme.MonoFinancialStyle
import com.example.ui.viewmodel.DashboardBundle
import kotlin.math.abs

@Composable
fun AnalyticsTabView(
    bundle: DashboardBundle,
    modifier: Modifier = Modifier
) {
    val snapshot = bundle.snapshot
    val currency = snapshot.currency
    val kMeans = snapshot.kMeansReport

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (snapshot.categoryBreakdown.isEmpty()) {
            item {
                ModelLimitationBanner(
                    message = "No transactions recorded for the current month yet. Add expenses or activate Demo Mode to view analytics."
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = "Category Distribution",
                            subtitle = "Total ${currency.format(snapshot.totalMonthlyExpense)} this month"
                        )
                        CategoryDonutChart(
                            breakdown = snapshot.categoryBreakdown,
                            currency = currency,
                            totalSpend = snapshot.totalMonthlyExpense
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(title = "Daily Velocity")
                        Spacer(modifier = Modifier.height(8.dp))
                        SpendingTrendLineChart(
                            dailySeries = snapshot.dailySpendSeries,
                            currency = currency
                        )
                    }
                }
            }

            // Month-over-Month Category Comparison
            item {
                SectionHeader(
                    title = "Month-over-Month Comparison",
                    subtitle = "Current month vs previous month (${currency.format(snapshot.previousMonthExpense)})"
                )
            }

            items(snapshot.categoryBreakdown) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.category.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item.momDeltaPct?.let { delta ->
                                    val sign = if (delta >= 0) "+" else "-"
                                    StatusChip(
                                        text = "$sign${abs(delta).toInt()}% MoM",
                                        color = if (delta > 10.0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = currency.format(item.amount),
                                    style = MaterialTheme.typography.titleSmall.merge(MonoFinancialStyle),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (item.percentageOfTotal / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = item.category.chartColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                        if (item.previousMonthAmount > 0.0) {
                            Text(
                                text = "Last month: ${currency.format(item.previousMonthAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // K-Means Behavioral Clustering Section
        item {
            SectionHeader(
                title = "K-Means Spending Clusters",
                subtitle = "Unsupervised behavioral grouping (${kMeans.sampleCount} txns)"
            )
        }

        if (!kMeans.modelTrained && kMeans.limitationMessage != null) {
            item {
                ModelLimitationBanner(message = kMeans.limitationMessage)
            }
        } else {
            items(kMeans.clusters) { cluster ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cluster.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${cluster.sharePercentage.toInt()}% of spend",
                                style = MaterialTheme.typography.labelMedium.merge(MonoFinancialStyle),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = cluster.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${cluster.transactionCount} transactions • Top: ${cluster.dominantCategory}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Avg ${currency.format(cluster.averageAmount)}",
                                style = MaterialTheme.typography.labelSmall.merge(MonoFinancialStyle),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
