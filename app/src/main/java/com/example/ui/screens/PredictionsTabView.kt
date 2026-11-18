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
import com.example.ui.components.ModelLimitationBanner
import com.example.ui.components.MonthlyForecastBarChart
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.theme.MonoFinancialStyle
import com.example.ui.viewmodel.DashboardBundle

@Composable
fun PredictionsTabView(
    bundle: DashboardBundle,
    modifier: Modifier = Modifier
) {
    val snapshot = bundle.snapshot
    val currency = snapshot.currency
    val forecast = snapshot.forecastReport

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (forecast.isInitialEstimate) {
            item {
                ModelLimitationBanner(message = forecast.modelExplanation)
            }
        }

        // Forecast Summary Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (forecast.isInitialEstimate) "Initial Monthly Estimate" else "Predicted Monthly Expenditure",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusChip(
                            text = if (forecast.modelTrained) "OLS Regression" else "Run-Rate Est.",
                            color = if (forecast.modelTrained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = currency.format(forecast.predictedMonthlyTotal),
                        style = MaterialTheme.typography.displaySmall.merge(MonoFinancialStyle),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Based on your current spending patterns, your estimated monthly expenses are ${currency.format(forecast.predictedMonthlyTotal)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "80% Confidence Range",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${currency.format(forecast.confidenceLow)} – ${currency.format(forecast.confidenceHigh)}",
                                    style = MaterialTheme.typography.labelLarge.merge(MonoFinancialStyle),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (forecast.rSquared != null) "Model Fit (R²)" else "Actual So Far",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (forecast.rSquared != null) {
                                        "${(forecast.rSquared * 100).toInt()}% (${forecast.monthsOfHistory} mo)"
                                    } else {
                                        currency.format(forecast.actualCurrentMonthSpend)
                                    },
                                    style = MaterialTheme.typography.labelLarge.merge(MonoFinancialStyle),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    if (forecast.modelTrained) {
                        Text(
                            text = forecast.modelExplanation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Historical vs Forecasted Chart
        if (forecast.monthlySeries.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeader(
                            title = "Monthly Spend vs Prediction",
                            subtitle = "Dashed line = Monthly Target (${currency.format(snapshot.monthlyBudgetTarget)})"
                        )
                        MonthlyForecastBarChart(
                            series = forecast.monthlySeries,
                            budgetTarget = snapshot.monthlyBudgetTarget,
                            currency = currency
                        )
                    }
                }
            }
        }

        // Category-wise Forecast vs Actual
        item {
            SectionHeader(
                title = "Category-Wise Projections",
                subtitle = "Actual month-to-date vs projected month-end"
            )
        }

        if (forecast.categoryForecasts.isEmpty()) {
            item {
                Text(
                    text = "Add expenses to see per-category end-of-month projections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(forecast.categoryForecasts) { item ->
                val overBudget = item.budgetLimit > 0.0 && item.predictedMonthlyTotal > item.budgetLimit
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                text = item.category.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (overBudget) {
                                    StatusChip(text = "Likely Over", color = MaterialTheme.colorScheme.error)
                                }
                                Text(
                                    text = "Est. ${currency.format(item.predictedMonthlyTotal)}",
                                    style = MaterialTheme.typography.titleSmall.merge(MonoFinancialStyle),
                                    color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val denominator = item.predictedMonthlyTotal.coerceAtLeast(1.0)
                        LinearProgressIndicator(
                            progress = { (item.actualToDate / denominator).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = item.category.chartColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Actual MTD: ${currency.format(item.actualToDate)}",
                                style = MaterialTheme.typography.labelSmall.merge(MonoFinancialStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.budgetLimit > 0.0) {
                                Text(
                                    text = "Cap: ${currency.format(item.budgetLimit)}",
                                    style = MaterialTheme.typography.labelSmall.merge(MonoFinancialStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
