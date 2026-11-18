package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ExpenseCategory
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.theme.MonoFinancialStyle
import com.example.ui.viewmodel.DashboardBundle
import kotlin.math.roundToLong

@Composable
fun BudgetPlannerScreen(
    bundle: DashboardBundle,
    onUpdateOverallTargets: (monthlyIncome: Double, monthlyBudgetTarget: Double) -> Unit,
    onUpsertCategoryBudget: (ExpenseCategory, Double, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = bundle.snapshot
    val currency = snapshot.currency

    var showOverallDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<ExpenseCategory?>(null) }

    val budgetsMap = remember(bundle.budgets) {
        bundle.budgets.associateBy { it.resolvedCategory() }
    }
    val currentSpentByCategory = remember(snapshot.categoryBreakdown) {
        snapshot.categoryBreakdown.associate { it.category to it.amount }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Monthly Overall Target Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Budget Target",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Income: ${currency.format(snapshot.totalIncome)}",
                                style = MaterialTheme.typography.bodySmall.merge(MonoFinancialStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showOverallDialog = true },
                            modifier = Modifier.testTag("edit_overall_budget_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit monthly income and budget target",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = currency.format(snapshot.totalMonthlyExpense),
                            style = MaterialTheme.typography.headlineMedium.merge(MonoFinancialStyle),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "of ${currency.format(snapshot.monthlyBudgetTarget)}",
                            style = MaterialTheme.typography.titleSmall.merge(MonoFinancialStyle),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val target = snapshot.monthlyBudgetTarget.coerceAtLeast(1.0)
                    val progress = (snapshot.totalMonthlyExpense / target).toFloat()
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (progress > 1f) MaterialTheme.colorScheme.error
                        else if (progress > 0.85f) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }
            }
        }

        // Personalised Recommendations Strip
        if (snapshot.budgetRecommendations.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TipsAndUpdates,
                            contentDescription = "Budget tip",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Smart Budget Coach",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            snapshot.budgetRecommendations.take(2).forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Category Caps
        item {
            SectionHeader(
                title = "Category Monthly Caps",
                subtitle = "Tap any category to set or adjust spending limits"
            )
        }

        items(ExpenseCategory.entries) { cat ->
            val budget = budgetsMap[cat]
            val limit = budget?.monthlyLimit ?: 0.0
            val spent = currentSpentByCategory[cat] ?: 0.0
            val threshold = budget?.alertThresholdPct ?: 80L
            val ratio = if (limit > 0.0) (spent / limit).toFloat() else 0f

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingCategory = cat },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = cat.displayName,
                                tint = cat.chartColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = cat.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (limit > 0.0) "Alert at $threshold%" else "No monthly cap set",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (limit > 0.0 && spent > limit) {
                                StatusChip(text = "OVER", color = MaterialTheme.colorScheme.error)
                            } else if (limit > 0.0 && ratio * 100f >= threshold) {
                                StatusChip(text = "NEAR CAP", color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text(
                                text = if (limit > 0.0) {
                                    "${currency.format(spent)} / ${currency.format(limit)}"
                                } else {
                                    currency.format(spent)
                                },
                                style = MaterialTheme.typography.labelLarge.merge(MonoFinancialStyle),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (limit > 0.0) {
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (spent > limit) MaterialTheme.colorScheme.error
                            else if (ratio * 100f >= threshold) MaterialTheme.colorScheme.tertiary
                            else cat.chartColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }
    }

    if (showOverallDialog) {
        var incomeText by remember {
            mutableStateOf(bundle.profile.monthlyIncome.toLong().toString())
        }
        var targetText by remember {
            mutableStateOf(bundle.profile.monthlyBudgetTarget.toLong().toString())
        }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showOverallDialog = false },
            title = { Text("Monthly Income & Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = incomeText,
                        onValueChange = { raw ->
                            incomeText = raw.filter { it.isDigit() || it == '.' }
                            error = null
                        },
                        label = { Text("Monthly Income (${currency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { raw ->
                            targetText = raw.filter { it.isDigit() || it == '.' }
                            error = null
                        },
                        label = { Text("Monthly Budget Target (${currency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    if (error != null) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val inc = incomeText.toDoubleOrNull()
                        val tgt = targetText.toDoubleOrNull()
                        if (inc == null || inc < 0.0 || tgt == null || tgt < 0.0) {
                            error = "Please enter valid non-negative numbers."
                        } else {
                            onUpdateOverallTargets(inc, tgt)
                            showOverallDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverallDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingCategory?.let { cat ->
        val currentBudget = budgetsMap[cat]
        var limitText by remember(cat) {
            mutableStateOf(
                (currentBudget?.monthlyLimit ?: 0.0).let {
                    if (it > 0.0) it.toLong().toString() else ""
                }
            )
        }
        var thresholdPct by remember(cat) {
            mutableFloatStateOf((currentBudget?.alertThresholdPct ?: 80L).toFloat())
        }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("${cat.displayName} Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { raw ->
                            limitText = raw.filter { it.isDigit() || it == '.' }
                            error = null
                        },
                        label = { Text("Monthly Cap (${currency.symbol})") },
                        placeholder = { Text("e.g., 5000 (0 to remove)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Column {
                        Text(
                            text = "Early Warning Alert: ${thresholdPct.roundToLong()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = thresholdPct,
                            onValueChange = { thresholdPct = it },
                            valueRange = 50f..100f
                        )
                    }
                    if (error != null) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = if (limitText.isBlank()) 0.0 else limitText.toDoubleOrNull()
                        if (parsed == null || parsed < 0.0 || parsed > 10_000_000.0) {
                            error = "Enter a valid budget between 0 and 10,000,000."
                        } else {
                            onUpsertCategoryBudget(cat, parsed, thresholdPct.roundToLong())
                            editingCategory = null
                        }
                    }
                ) {
                    Text("Save Cap")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("Cancel") }
            }
        )
    }
}
