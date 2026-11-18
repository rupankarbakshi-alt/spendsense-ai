package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CurrencyOption
import com.example.data.preferences.ThemeMode
import com.example.ui.components.SectionHeader
import com.example.ui.viewmodel.DashboardBundle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(
    bundle: DashboardBundle,
    themeMode: ThemeMode,
    isBusy: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSaveProfile: (displayName: String, currencyCode: String, monthlyIncome: Double, monthlyBudgetTarget: Double) -> Unit,
    onLoadDemo: () -> Unit,
    onClearDemo: () -> Unit,
    onBuildExportCsv: () -> String,
    onDeleteAllData: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = bundle.profile

    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var selectedCurrency by remember(profile.currencyCode) {
        mutableStateOf(CurrencyOption.fromCode(profile.currencyCode))
    }
    var incomeText by remember(profile.monthlyIncome) {
        mutableStateOf(profile.monthlyIncome.toLong().toString())
    }
    var budgetText by remember(profile.monthlyBudgetTarget) {
        mutableStateOf(profile.monthlyBudgetTarget.toLong().toString())
    }
    var profileError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Account Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "User profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(11.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName.ifBlank { "SpendSense User" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = profile.email.ifBlank { "Authenticated via Google" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = "Secured account",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Theme Mode Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = "Appearance",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Appearance Theme",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }
            }
        }

        // Financial Profile Editor
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
                    SectionHeader(title = "Financial Settings")

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it.take(60)
                            profileError = null
                        },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Preferred Currency",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CurrencyOption.entries.forEach { option ->
                            FilterChip(
                                selected = selectedCurrency == option,
                                onClick = { selectedCurrency = option },
                                label = { Text("${option.symbol} ${option.code}") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = incomeText,
                            onValueChange = { raw ->
                                incomeText = raw.filter { it.isDigit() || it == '.' }
                                profileError = null
                            },
                            label = { Text("Monthly Income") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = budgetText,
                            onValueChange = { raw ->
                                budgetText = raw.filter { it.isDigit() || it == '.' }
                                profileError = null
                            },
                            label = { Text("Budget Target") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (profileError != null) {
                        Text(
                            text = profileError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            val inc = incomeText.toDoubleOrNull()
                            val tgt = budgetText.toDoubleOrNull()
                            when {
                                displayName.isBlank() -> profileError = "Display name cannot be empty."
                                inc == null || inc < 0.0 -> profileError = "Enter a valid income."
                                tgt == null || tgt < 0.0 -> profileError = "Enter a valid budget target."
                                else -> onSaveProfile(displayName, selectedCurrency.code, inc, tgt)
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_profile_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Profile Settings")
                    }
                }
            }
        }

        // Demo Mode Controls
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Science,
                            contentDescription = "Demo Mode",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Demo Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Explore K-Means clusters, Isolation Forest anomalies, and OLS regression forecasts with clearly labeled sample records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onLoadDemo,
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_load_demo_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Load Demo Data")
                        }
                        OutlinedButton(
                            onClick = onClearDemo,
                            enabled = !isBusy && profile.demoModeActive,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_clear_demo_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear Demo")
                        }
                    }
                }
            }
        }

        // Privacy, Export & Data Deletion
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
                        title = "Privacy & Data Control",
                        subtitle = "Zero-trust per-user Firestore isolation. No external bank linking."
                    )

                    OutlinedButton(
                        onClick = {
                            val csv = onBuildExportCsv()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "SpendSense AI Financial Export")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, "Export SpendSense AI Data")
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("export_csv_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Personal Financial Data (CSV)")
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("delete_all_data_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Financial Records")
                    }

                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("sign_out_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete All Financial Records?") },
            text = {
                Text("This permanently deletes all your saved expenses, category budgets, and learned rules from SpendSense AI. Your account profile remains active.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
