package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.model.CategoryRule
import com.example.data.model.CurrencyOption
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.ml.ClassificationResult
import com.example.ui.viewmodel.SpendSenseViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditExpenseScreen(
    existingExpense: Expense?,
    currency: CurrencyOption,
    userRules: List<CategoryRule>,
    isBusy: Boolean,
    viewModel: SpendSenseViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var title by remember(existingExpense) { mutableStateOf(existingExpense?.title.orEmpty()) }
    var merchant by remember(existingExpense) { mutableStateOf(existingExpense?.merchant.orEmpty()) }
    var amountText by remember(existingExpense) {
        mutableStateOf(
            existingExpense?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            }.orEmpty()
        )
    }
    var selectedCategory by remember(existingExpense) {
        mutableStateOf(existingExpense?.resolvedCategory() ?: ExpenseCategory.FOOD_DINING)
    }
    var userManuallyPickedCategory by remember(existingExpense) {
        mutableStateOf(existingExpense != null)
    }
    var predictedResult by remember { mutableStateOf<ClassificationResult?>(null) }
    var aiReasoning by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }

    var paymentMethod by remember(existingExpense) {
        mutableStateOf(PaymentMethod.fromDisplayName(existingExpense?.paymentMethod))
    }
    var notes by remember(existingExpense) { mutableStateOf(existingExpense?.notes.orEmpty()) }
    var dateMillis by remember(existingExpense) {
        mutableLongStateOf(existingExpense?.dateEpochMillis ?: System.currentTimeMillis())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Live ML smart categorization as user types
    LaunchedEffect(title, merchant, notes, amountText, userRules) {
        if (!userManuallyPickedCategory && (title.isNotBlank() || merchant.isNotBlank())) {
            val result = viewModel.predictCategoryLocally(
                title = title,
                merchant = merchant,
                notes = notes,
                amount = amountText.toDoubleOrNull(),
                rules = userRules
            )
            predictedResult = result
            selectedCategory = result.category
        }
    }

    val dateFormatter = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingExpense == null) "Add Expense" else "Edit Expense",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it.take(100)
                    validationError = null
                },
                label = { Text("Expense Title *") },
                placeholder = { Text("e.g., Zomato Dinner, Uber Ride, Amazon Order") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_title_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            amountText = filtered
                            validationError = null
                        }
                    },
                    label = { Text("Amount (${currency.symbol}) *") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("expense_amount_input")
                )

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it.take(80) },
                    label = { Text("Merchant") },
                    placeholder = { Text("e.g., Swiggy") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("expense_merchant_input")
                )
            }

            // Smart Classification Banner + Gemini AI Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Smart classification",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                val pred = predictedResult
                                Text(
                                    text = if (pred != null) {
                                        "Auto-Matched: ${pred.category.displayName} (${(pred.confidence * 100).toInt()}%)"
                                    } else {
                                        "Smart ML Categorisation Ready"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = aiReasoning ?: pred?.matchedSource
                                        ?: "Type a title/merchant or tap Gemini AI Classify",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (title.isBlank() && merchant.isBlank()) {
                                    aiError = "Enter a title or merchant first for Gemini AI."
                                    return@OutlinedButton
                                }
                                aiError = null
                                viewModel.classifyExpenseWithGemini(
                                    title = title,
                                    merchant = merchant,
                                    notes = notes,
                                    amount = amountText.toDoubleOrNull()
                                ) { res ->
                                    res.onSuccess { ai ->
                                        selectedCategory = ai.category
                                        userManuallyPickedCategory = false
                                        predictedResult = ClassificationResult(
                                            ai.category,
                                            ai.confidence,
                                            "Gemini AI"
                                        )
                                        aiReasoning = "Gemini (${(ai.confidence * 100).toInt()}%): ${ai.reasoning}"
                                    }.onFailure { err ->
                                        aiError = err.localizedMessage ?: "Gemini classification failed."
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("ai_classify_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("AI Classify", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (aiError != null) {
                        Text(
                            text = aiError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 9 Categories Grid
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpenseCategory.entries.forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategory = category
                            userManuallyPickedCategory = true
                        },
                        label = { Text(category.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = category.chartColor.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // Payment Method
            Text(
                text = "Payment Method",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PaymentMethod.entries.forEach { pm ->
                    FilterChip(
                        selected = paymentMethod == pm,
                        onClick = { paymentMethod = pm },
                        label = { Text(pm.displayName) }
                    )
                }
            }

            // Date Picker Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(Calendar.YEAR, y)
                                cal.set(Calendar.MONTH, m)
                                cal.set(Calendar.DAY_OF_MONTH, d)
                                dateMillis = cal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = "Select date",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Transaction Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateFormatter.format(Date(dateMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(300) },
                label = { Text("Notes (Optional)") },
                placeholder = { Text("Add context or receipt notes...") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    val parsedAmount = amountText.toDoubleOrNull()
                    when {
                        trimmedTitle.isEmpty() -> {
                            validationError = "Please enter an expense title."
                        }
                        parsedAmount == null || parsedAmount <= 0.0 -> {
                            validationError = "Please enter a valid amount greater than 0."
                        }
                        parsedAmount > 10_000_000.0 -> {
                            validationError = "Amount cannot exceed 10,000,000."
                        }
                        else -> {
                            viewModel.saveExpense(
                                existingExpense = existingExpense,
                                title = trimmedTitle,
                                merchant = merchant,
                                amount = parsedAmount,
                                category = selectedCategory,
                                paymentMethod = paymentMethod,
                                notes = notes,
                                dateEpochMillis = dateMillis,
                                wasAutoPredicted = !userManuallyPickedCategory,
                                predictedCategory = predictedResult?.category,
                                onSaved = onBack
                            )
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("save_expense_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (existingExpense == null) "Save Expense" else "Update Expense",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
