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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.ui.components.ExpenseRowCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoFinancialStyle
import com.example.ui.viewmodel.DashboardBundle

@Composable
fun TransactionHistoryScreen(
    bundle: DashboardBundle,
    onEditExpense: (Expense) -> Unit,
    onQuickChangeCategory: (Expense, ExpenseCategory) -> Unit,
    onDeleteExpense: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var onlyAnomalies by remember { mutableStateOf(false) }
    var categoryEditTarget by remember { mutableStateOf<Expense?>(null) }

    val anomalyIds = bundle.snapshot.anomalyExpenseIds
    val currency = bundle.snapshot.currency

    val filtered = remember(bundle.expenses, searchQuery, selectedCategory, onlyAnomalies, anomalyIds) {
        bundle.expenses.filter { exp ->
            val matchesQuery = searchQuery.isBlank() ||
                exp.title.contains(searchQuery, ignoreCase = true) ||
                exp.merchant.contains(searchQuery, ignoreCase = true) ||
                exp.category.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == null || exp.resolvedCategory() == selectedCategory
            val matchesAnomaly = !onlyAnomalies || anomalyIds.contains(exp.id)
            matchesQuery && matchesCat && matchesAnomaly
        }
    }

    val totalFiltered = filtered.sumOf { it.amount }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(
                title = "Transaction History",
                subtitle = "${filtered.size} transaction(s) • Total ${currency.format(totalFiltered)}"
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
                placeholder = { Text("Search merchant, title, or category...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null && !onlyAnomalies,
                        onClick = {
                            selectedCategory = null
                            onlyAnomalies = false
                        },
                        label = { Text("All (${bundle.expenses.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = onlyAnomalies,
                        onClick = { onlyAnomalies = !onlyAnomalies },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Anomalies (${anomalyIds.size})") }
                    )
                }
                items(ExpenseCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                        },
                        label = { Text(cat.displayName) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No matching transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try clearing filters or add a new expense using the + button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id.ifBlank { it.hashCode().toString() } }) { exp ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExpenseRowCard(
                            expense = exp,
                            currency = currency,
                            isAnomaly = anomalyIds.contains(exp.id),
                            onClick = { onEditExpense(exp) },
                            onDelete = { if (exp.id.isNotBlank()) onDeleteExpense(exp.id) }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (exp.notes.isNotBlank()) exp.notes else "Tap card to edit details",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { categoryEditTarget = exp }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Quick edit category",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Fix Category",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    categoryEditTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { categoryEditTarget = null },
            title = { Text("Correct Category & Train Classifier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${target.title} (${currency.format(target.amount)})",
                        style = MaterialTheme.typography.bodyMedium.merge(MonoFinancialStyle)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpenseCategory.entries.forEach { cat ->
                        val isCurrent = target.resolvedCategory() == cat
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onQuickChangeCategory(target, cat)
                                    categoryEditTarget = null
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = cat.displayName,
                                    tint = cat.chartColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = cat.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { categoryEditTarget = null }) {
                    Text("Close")
                }
            }
        )
    }
}
