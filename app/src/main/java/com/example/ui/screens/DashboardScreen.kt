package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Expense
import com.example.ml.InsightType
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.EmptyFinanceOnboardingCard
import com.example.ui.components.ExpenseRowCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SpendingTrendLineChart
import com.example.ui.theme.MonoFinancialStyle
import com.example.ui.viewmodel.DashboardBundle

@Composable
fun DashboardScreen(
    bundle: DashboardBundle,
    isBusy: Boolean,
    onAddExpense: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenPredictions: () -> Unit,
    onOpenBudgets: () -> Unit,
    onLoadDemo: () -> Unit,
    onClearDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = bundle.snapshot
    val currency = snapshot.currency
    val hasAnyExpenses = bundle.expenses.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (bundle.profile.demoModeActive) {
            item {
                DemoModeBanner(onClearDemo = onClearDemo)
            }
        }

        // Hero Financial Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0E1F3D),
                                    Color(0xFF142D54),
                                    Color(0xFF0B3B38)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Remaining Balance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = currency.format(snapshot.remainingBalance),
                                    style = MaterialTheme.typography.displaySmall.merge(MonoFinancialStyle),
                                    color = if (snapshot.remainingBalance >= 0) Color.White else Color(0xFFF87171)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenBudgets)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Savings,
                                        contentDescription = "Monthly savings",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${snapshot.savingsRatePct.toInt()}% Saved",
                                        style = MaterialTheme.typography.labelMedium.merge(MonoFinancialStyle),
                                        color = Color(0xFFA7F3D0)
                                    )
                                }
                            }
                        }

                        // Monthly Budget Target Progress
                        if (snapshot.monthlyBudgetTarget > 0.0) {
                            val ratio = (snapshot.totalMonthlyExpense / snapshot.monthlyBudgetTarget).toFloat()
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Spent ${currency.format(snapshot.totalMonthlyExpense)}",
                                        style = MaterialTheme.typography.labelMedium.merge(MonoFinancialStyle),
                                        color = Color(0xFFE2E8F0)
                                    )
                                    Text(
                                        text = "Target ${currency.format(snapshot.monthlyBudgetTarget)}",
                                        style = MaterialTheme.typography.labelMedium.merge(MonoFinancialStyle),
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { ratio.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (ratio > 1f) Color(0xFFF87171)
                                    else if (ratio > 0.85f) Color(0xFFF59E0B)
                                    else Color(0xFF10B981),
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )
                            }
                        }

                        // 3 Metric Tiles: Income, Spent, Savings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeroStatChip(
                                label = "Income",
                                value = currency.format(snapshot.totalIncome),
                                accent = Color(0xFF38BDF8),
                                modifier = Modifier.weight(1f)
                            )
                            HeroStatChip(
                                label = "Month Spend",
                                value = currency.format(snapshot.totalMonthlyExpense),
                                accent = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                            HeroStatChip(
                                label = "Savings",
                                value = currency.format(snapshot.monthlySavings),
                                accent = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (!hasAnyExpenses) {
            item {
                EmptyFinanceOnboardingCard(
                    onAddExpense = onAddExpense,
                    onLoadDemo = onLoadDemo,
                    isBusy = isBusy
                )
            }
        } else {
            // Predicted Monthly Expenditure Quick Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPredictions)
                        .testTag("dashboard_prediction_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TrendingUp,
                                contentDescription = "Monthly prediction",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (snapshot.forecastReport.isInitialEstimate) {
                                    "Estimated Monthly Spend (Initial)"
                                } else {
                                    "ML Predicted Monthly Spend"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currency.format(snapshot.forecastReport.predictedMonthlyTotal),
                                style = MaterialTheme.typography.headlineSmall.merge(MonoFinancialStyle),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Range: ${currency.format(snapshot.forecastReport.confidenceLow)} – ${currency.format(snapshot.forecastReport.confidenceHigh)}",
                                style = MaterialTheme.typography.labelSmall.merge(MonoFinancialStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Open predictions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Category Breakdown Donut Chart
            if (snapshot.categoryBreakdown.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeader(
                                title = "Category Breakdown",
                                subtitle = "Tap a category to inspect share"
                            )
                            CategoryDonutChart(
                                breakdown = snapshot.categoryBreakdown,
                                currency = currency,
                                totalSpend = snapshot.totalMonthlyExpense
                            )
                        }
                    }
                }
            }

            // Daily Spending Trend Line Chart
            if (snapshot.dailySpendSeries.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(title = "Spending Trend")
                            Spacer(modifier = Modifier.height(8.dp))
                            SpendingTrendLineChart(
                                dailySeries = snapshot.dailySpendSeries,
                                currency = currency
                            )
                        }
                    }
                }
            }

            // AI-Generated Insights Preview
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "AI & ML Insights",
                        subtitle = "${snapshot.isolationForestReport.flaggedTransactions.size} unusual activity alert(s)",
                        actionLabel = "View All",
                        onActionClick = onOpenInsights
                    )
                    snapshot.insights.take(3).forEach { insight ->
                        val accent = when (insight.type) {
                            InsightType.POSITIVE -> MaterialTheme.colorScheme.primary
                            InsightType.WARNING -> MaterialTheme.colorScheme.tertiary
                            InsightType.ANOMALY -> MaterialTheme.colorScheme.error
                            InsightType.INFO -> MaterialTheme.colorScheme.secondary
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenInsights),
                            shape = RoundedCornerShape(14.dp),
                            color = accent.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (insight.type == InsightType.ANOMALY || insight.type == InsightType.WARNING) {
                                        Icons.Outlined.WarningAmber
                                    } else {
                                        Icons.Outlined.AutoAwesome
                                    },
                                    contentDescription = insight.title,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = insight.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = insight.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Transactions
            item {
                SectionHeader(
                    title = "Recent Transactions",
                    actionLabel = "See History",
                    onActionClick = onOpenHistory
                )
            }
            items(snapshot.recentTransactions, key = { it.id.ifBlank { it.hashCode().toString() } }) { exp ->
                ExpenseRowCard(
                    expense = exp,
                    currency = currency,
                    isAnomaly = snapshot.anomalyExpenseIds.contains(exp.id),
                    onClick = { onEditExpense(exp) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun HeroStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.merge(MonoFinancialStyle),
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
        }
    }
}
