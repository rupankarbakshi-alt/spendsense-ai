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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.ml.AnomalySeverity
import com.example.ml.InsightType
import com.example.ui.components.ModelLimitationBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.theme.MonoFinancialStyle
import com.example.ui.viewmodel.AdvisorChatState
import com.example.ui.viewmodel.AiBriefingState
import com.example.ui.viewmodel.DashboardBundle
import com.example.ui.viewmodel.SpendSenseViewModel

@Composable
fun AiInsightsTabView(
    bundle: DashboardBundle,
    briefingState: AiBriefingState,
    advisorState: AdvisorChatState,
    viewModel: SpendSenseViewModel,
    modifier: Modifier = Modifier
) {
    val snapshot = bundle.snapshot
    val currency = snapshot.currency
    val anomalyReport = snapshot.isolationForestReport
    var questionText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Isolation Forest Anomaly Detection
        item {
            SectionHeader(
                title = "Isolation Forest Anomaly Detection",
                subtitle = if (anomalyReport.modelTrained) {
                    "64-tree forest trained on ${anomalyReport.sampleCount} transactions"
                } else {
                    "Preliminary check (${anomalyReport.sampleCount}/${anomalyReport.minimumRequiredSamples} samples)"
                }
            )
        }

        if (anomalyReport.limitationMessage != null) {
            item {
                ModelLimitationBanner(message = anomalyReport.limitationMessage)
            }
        }

        if (anomalyReport.flaggedTransactions.isEmpty() && anomalyReport.categorySpikes.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = "No anomalies",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "No unusual spending spikes detected across your transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            items(anomalyReport.flaggedTransactions) { item ->
                val badgeColor = if (item.severity == AnomalySeverity.HIGH) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = "Anomaly alert",
                                    tint = badgeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.expense.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            StatusChip(
                                text = "Score ${"%.2f".format(item.anomalyScore)}",
                                color = badgeColor
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currency.format(item.expense.amount),
                                style = MaterialTheme.typography.titleSmall.merge(MonoFinancialStyle),
                                color = badgeColor
                            )
                        }
                    }
                }
            }

            items(anomalyReport.categorySpikes) { spike ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = "Category spike",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = spike,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Data-Driven Behavioral Insights & Budget Recommendations
        item {
            SectionHeader(
                title = "Personalised Recommendations",
                subtitle = "Computed directly from your spending patterns & budgets"
            )
        }

        items(snapshot.budgetRecommendations) { rec ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TipsAndUpdates,
                        contentDescription = "Recommendation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = rec,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Gemini 3.8 Flash Deep AI Briefing
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Gemini AI Coach",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Gemini AI Financial Coach",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Deep generative synthesis via Firebase AI Logic",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.generateAiBriefing(snapshot) },
                        enabled = briefingState !is AiBriefingState.Loading && bundle.expenses.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("generate_ai_briefing_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (briefingState is AiBriefingState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing Spending Patterns...")
                        } else {
                            Text("Generate AI Coaching Briefing")
                        }
                    }

                    when (briefingState) {
                        is AiBriefingState.Error -> {
                            Text(
                                text = briefingState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        is AiBriefingState.Success -> {
                            val b = briefingState.briefing
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = b.executiveSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Pattern Observed: ${b.topHabitObservation}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    b.actionSteps.forEach { step ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircleOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = step,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (b.riskFlag.isNotBlank()) {
                                        Text(
                                            text = "Risk Watch: ${b.riskFlag}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }

                    // Ask SpendSense AI Advisor (Streaming)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            placeholder = { Text("Ask AI: e.g., How can I cut 15% this month?") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_advisor_input")
                        )
                        IconButton(
                            onClick = {
                                viewModel.askFinancialAdvisor(questionText, snapshot)
                                questionText = ""
                            },
                            enabled = questionText.isNotBlank() && advisorState !is AdvisorChatState.Loading,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Ask Gemini AI Advisor",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    when (advisorState) {
                        is AdvisorChatState.Loading -> {
                            Text(
                                text = "SpendSense AI is thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AdvisorChatState.Streaming -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = advisorState.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        is AdvisorChatState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = advisorState.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        is AdvisorChatState.Error -> {
                            Text(
                                text = advisorState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        AdvisorChatState.Idle -> Unit
                    }
                }
            }
        }
    }
}
