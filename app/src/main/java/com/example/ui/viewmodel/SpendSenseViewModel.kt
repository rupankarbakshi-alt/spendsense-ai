package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiClassificationResponse
import com.example.data.ai.AiCoachingBriefing
import com.example.data.ai.GeminiAiService
import com.example.data.model.CategoryBudget
import com.example.data.model.CategoryRule
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.UserProfile
import com.example.data.repository.FinanceRepository
import com.example.ml.ClassificationResult
import com.example.ml.ExpenseClassifier
import com.example.ml.MlAnalyticsEngine
import com.example.ml.SpendIntelligenceSnapshot
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiBriefingState {
    data object Idle : AiBriefingState
    data object Loading : AiBriefingState
    data class Success(val briefing: AiCoachingBriefing) : AiBriefingState
    data class Error(val message: String) : AiBriefingState
}

sealed interface AdvisorChatState {
    data object Idle : AdvisorChatState
    data object Loading : AdvisorChatState
    data class Streaming(val text: String) : AdvisorChatState
    data class Success(val text: String) : AdvisorChatState
    data class Error(val message: String) : AdvisorChatState
}

data class DashboardBundle(
    val profile: UserProfile,
    val expenses: List<Expense>,
    val budgets: List<CategoryBudget>,
    val categoryRules: List<CategoryRule>,
    val snapshot: SpendIntelligenceSnapshot
)

class SpendSenseViewModel(
    private val repository: FinanceRepository,
    private val aiService: GeminiAiService,
    private val currentUserId: String
) : ViewModel() {

    private companion object {
        const val TAG = "SpendSenseVM"
        const val STOP_TIMEOUT_MS = 5000L
    }

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                repository.ensureUserProfileExists(
                    displayName = user.displayName ?: "SpendSense User",
                    email = user.email.orEmpty()
                )
            }
        }
    }

    val dashboardState: StateFlow<UiState<DashboardBundle>> = combine(
        repository.observeProfile(currentUserId),
        repository.observeExpenses(currentUserId),
        repository.observeBudgets(currentUserId),
        repository.observeCategoryRules(currentUserId)
    ) { profile, expenses, budgets, rules ->
        val resolvedProfile = profile ?: UserProfile(
            userId = currentUserId,
            displayName = Firebase.auth.currentUser?.displayName ?: "SpendSense User",
            email = Firebase.auth.currentUser?.email.orEmpty()
        )
        val snapshot = MlAnalyticsEngine.analyze(
            profile = resolvedProfile,
            expenses = expenses,
            budgets = budgets
        )
        UiState.Success(
            DashboardBundle(
                profile = resolvedProfile,
                expenses = expenses,
                budgets = budgets,
                categoryRules = rules,
                snapshot = snapshot
            )
        ) as UiState<DashboardBundle>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing dashboard bundle", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load financial data."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading
    )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _aiBriefingState = MutableStateFlow<AiBriefingState>(AiBriefingState.Idle)
    val aiBriefingState: StateFlow<AiBriefingState> = _aiBriefingState.asStateFlow()

    private val _advisorChatState = MutableStateFlow<AdvisorChatState>(AdvisorChatState.Idle)
    val advisorChatState: StateFlow<AdvisorChatState> = _advisorChatState.asStateFlow()

    private var advisorJob: Job? = null

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun predictCategoryLocally(
        title: String,
        merchant: String,
        notes: String,
        amount: Double?,
        rules: List<CategoryRule>
    ): ClassificationResult {
        return ExpenseClassifier.classify(
            title = title,
            merchant = merchant,
            notes = notes,
            amount = amount,
            userRules = rules
        )
    }

    fun classifyExpenseWithGemini(
        title: String,
        merchant: String,
        notes: String,
        amount: Double?,
        onResult: (Result<AiClassificationResponse>) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = aiService.classifyExpense(title, merchant, notes, amount)
            _isBusy.value = false
            onResult(result)
        }
    }

    fun saveExpense(
        existingExpense: Expense?,
        title: String,
        merchant: String,
        amount: Double,
        category: ExpenseCategory,
        paymentMethod: PaymentMethod,
        notes: String,
        dateEpochMillis: Long,
        wasAutoPredicted: Boolean,
        predictedCategory: ExpenseCategory?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val userCorrectedCategory = predictedCategory != null && predictedCategory != category
            val expense = Expense(
                id = existingExpense?.id.orEmpty(),
                userId = currentUserId,
                title = title.trim(),
                merchant = merchant.trim(),
                amount = amount,
                category = category.displayName,
                paymentMethod = paymentMethod.displayName,
                notes = notes.trim(),
                dateEpochMillis = dateEpochMillis,
                autoCategorized = wasAutoPredicted && !userCorrectedCategory,
                demoRecord = existingExpense?.demoRecord ?: false
            )

            val result = if (existingExpense == null) {
                repository.addExpense(expense)
            } else {
                repository.updateExpense(expense)
            }

            if (result.isSuccess) {
                // Adaptive ML learning: save user correction rule if they overrode category
                val learnKeyword = merchant.trim().ifBlank { title.trim().split(" ").firstOrNull().orEmpty() }
                if ((userCorrectedCategory || existingExpense != null) && learnKeyword.length >= 3) {
                    repository.saveCategoryRule(learnKeyword, category)
                }
                _statusMessage.value = if (existingExpense == null) {
                    "Expense saved & ML insights updated"
                } else {
                    "Expense updated & category preference learned"
                }
                _isBusy.value = false
                onSaved()
            } else {
                _statusMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save expense"
                _isBusy.value = false
            }
        }
    }

    fun updateExpenseCategory(expense: Expense, newCategory: ExpenseCategory) {
        viewModelScope.launch {
            val updated = expense.copy(
                category = newCategory.displayName,
                autoCategorized = false
            )
            val result = repository.updateExpense(updated)
            if (result.isSuccess) {
                val kw = expense.merchant.ifBlank { expense.title.split(" ").firstOrNull().orEmpty() }
                if (kw.length >= 3) {
                    repository.saveCategoryRule(kw, newCategory)
                }
                _statusMessage.value = "Updated to ${newCategory.displayName} (classifier trained)"
            } else {
                _statusMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update category"
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            val result = repository.deleteExpense(expenseId)
            _statusMessage.value = if (result.isSuccess) {
                "Transaction deleted"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to delete transaction"
            }
        }
    }

    fun updateProfileSettings(
        displayName: String,
        currencyCode: String,
        monthlyIncome: Double,
        monthlyBudgetTarget: Double
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfile(displayName, currencyCode, monthlyIncome, monthlyBudgetTarget)
            _isBusy.value = false
            _statusMessage.value = if (result.isSuccess) {
                "Financial profile updated"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun upsertBudget(category: ExpenseCategory, monthlyLimit: Double, alertThresholdPct: Long) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.upsertBudget(category, monthlyLimit, alertThresholdPct)
            _isBusy.value = false
            _statusMessage.value = if (result.isSuccess) {
                "${category.displayName} budget updated"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to update budget"
            }
        }
    }

    fun loadDemoDataset() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.loadDemoDataset()
            _isBusy.value = false
            _statusMessage.value = if (result.isSuccess) {
                "Demo Mode loaded with 4 months of labeled sample expenses"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to load demo data"
            }
        }
    }

    fun clearDemoDataset() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.clearDemoDataset()
            _isBusy.value = false
            _statusMessage.value = if (result.isSuccess) {
                "Demo sample transactions removed"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to clear demo data"
            }
        }
    }

    fun deleteAllFinancialData() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteAllUserData()
            _isBusy.value = false
            _aiBriefingState.value = AiBriefingState.Idle
            _advisorChatState.value = AdvisorChatState.Idle
            _statusMessage.value = if (result.isSuccess) {
                "All personal financial records deleted"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to delete data"
            }
        }
    }

    fun generateAiBriefing(snapshot: SpendIntelligenceSnapshot) {
        viewModelScope.launch {
            _aiBriefingState.value = AiBriefingState.Loading
            val result = aiService.generateCoachingBriefing(snapshot)
            _aiBriefingState.value = result.fold(
                onSuccess = { AiBriefingState.Success(it) },
                onFailure = { AiBriefingState.Error(it.localizedMessage ?: "Gemini AI briefing failed.") }
            )
        }
    }

    fun askFinancialAdvisor(question: String, snapshot: SpendIntelligenceSnapshot) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        advisorJob?.cancel()
        advisorJob = viewModelScope.launch {
            _advisorChatState.value = AdvisorChatState.Loading
            try {
                var finalAnswer = ""
                aiService.askAdvisorStream(trimmed, snapshot).collect { partial ->
                    finalAnswer = partial
                    _advisorChatState.value = AdvisorChatState.Streaming(partial)
                }
                if (finalAnswer.isNotBlank()) {
                    _advisorChatState.value = AdvisorChatState.Success(finalAnswer)
                } else {
                    _advisorChatState.value = AdvisorChatState.Error("No answer returned from Gemini.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Advisor streaming failed", e)
                _advisorChatState.value = AdvisorChatState.Error(
                    e.localizedMessage ?: "Failed to reach SpendSense AI advisor."
                )
            }
        }
    }

    fun buildExportCsv(bundle: DashboardBundle): String =
        repository.buildExportCsv(bundle.profile, bundle.expenses)
}
