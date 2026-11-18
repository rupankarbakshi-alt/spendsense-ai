package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.AdvisorChatState
import com.example.ui.viewmodel.AiBriefingState
import com.example.ui.viewmodel.DashboardBundle
import com.example.ui.viewmodel.SpendSenseViewModel

enum class IntelligenceSubTab(val label: String) {
    ANALYTICS("Analytics"),
    INSIGHTS("AI Insights"),
    PREDICTIONS("Predictions")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceHubScreen(
    selectedTab: IntelligenceSubTab,
    onSelectTab: (IntelligenceSubTab) -> Unit,
    bundle: DashboardBundle,
    briefingState: AiBriefingState,
    advisorState: AdvisorChatState,
    viewModel: SpendSenseViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            IntelligenceSubTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = IntelligenceSubTab.entries.size
                    ),
                    modifier = Modifier.testTag("intelligence_tab_${tab.name.lowercase()}")
                ) {
                    Text(tab.label)
                }
            }
        }

        when (selectedTab) {
            IntelligenceSubTab.ANALYTICS -> AnalyticsTabView(bundle = bundle)
            IntelligenceSubTab.INSIGHTS -> AiInsightsTabView(
                bundle = bundle,
                briefingState = briefingState,
                advisorState = advisorState,
                viewModel = viewModel
            )
            IntelligenceSubTab.PREDICTIONS -> PredictionsTabView(bundle = bundle)
        }
    }
}
