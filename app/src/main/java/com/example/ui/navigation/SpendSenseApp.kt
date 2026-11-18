package com.example.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.GeminiAiService
import com.example.data.model.Expense
import com.example.data.preferences.ThemeMode
import com.example.data.repository.FinanceRepository
import com.example.ui.screens.AddEditExpenseScreen
import com.example.ui.screens.AuthWelcomeScreen
import com.example.ui.screens.BudgetPlannerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.IntelligenceHubScreen
import com.example.ui.screens.IntelligenceSubTab
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.TransactionHistoryScreen
import com.example.ui.viewmodel.SpendSenseViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Home", Icons.Filled.Home, Icons.Outlined.Home),
    HISTORY("History", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    INTELLIGENCE("Intel", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    BUDGETS("Budgets", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun SpendSenseApp(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthWelcomeScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            modifier = modifier
        )
    } else {
        key(user.uid) {
            AuthenticatedSpendSenseHost(
                currentUserId = user.uid,
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedSpendSenseHost(
    currentUserId: String,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SpendSenseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                SpendSenseViewModel(
                    repository = FinanceRepository(app),
                    aiService = GeminiAiService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val aiBriefingState by viewModel.aiBriefingState.collectAsStateWithLifecycle()
    val advisorState by viewModel.advisorChatState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MainNavTab.DASHBOARD) }
    var intelligenceSubTab by remember { mutableStateOf(IntelligenceSubTab.ANALYTICS) }
    var isAddingOrEditingExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    if (isAddingOrEditingExpense) {
        val successBundle = (dashboardState as? UiState.Success)?.data
        if (successBundle != null) {
            AddEditExpenseScreen(
                existingExpense = editingExpense,
                currency = successBundle.snapshot.currency,
                userRules = successBundle.categoryRules,
                isBusy = isBusy,
                viewModel = viewModel,
                onBack = {
                    isAddingOrEditingExpense = false
                    editingExpense = null
                },
                modifier = modifier
            )
            return
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                MainNavTab.DASHBOARD -> "SpendSense AI"
                                MainNavTab.HISTORY -> "Activity"
                                MainNavTab.INTELLIGENCE -> "Intelligence Hub"
                                MainNavTab.BUDGETS -> "Budget Planner"
                                MainNavTab.PROFILE -> "Profile & Security"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                if (selectedTab != MainNavTab.PROFILE && dashboardState is UiState.Success) {
                    FloatingActionButton(
                        onClick = {
                            editingExpense = null
                            isAddingOrEditingExpense = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_expense")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add Expense")
                    }
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                when (val state = dashboardState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    is UiState.Success -> {
                        val bundle = state.data
                        when (selectedTab) {
                            MainNavTab.DASHBOARD -> DashboardScreen(
                                bundle = bundle,
                                isBusy = isBusy,
                                onAddExpense = {
                                    editingExpense = null
                                    isAddingOrEditingExpense = true
                                },
                                onEditExpense = { exp ->
                                    editingExpense = exp
                                    isAddingOrEditingExpense = true
                                },
                                onOpenHistory = { selectedTab = MainNavTab.HISTORY },
                                onOpenInsights = {
                                    intelligenceSubTab = IntelligenceSubTab.INSIGHTS
                                    selectedTab = MainNavTab.INTELLIGENCE
                                },
                                onOpenPredictions = {
                                    intelligenceSubTab = IntelligenceSubTab.PREDICTIONS
                                    selectedTab = MainNavTab.INTELLIGENCE
                                },
                                onOpenBudgets = { selectedTab = MainNavTab.BUDGETS },
                                onLoadDemo = { viewModel.loadDemoDataset() },
                                onClearDemo = { viewModel.clearDemoDataset() }
                            )

                            MainNavTab.HISTORY -> TransactionHistoryScreen(
                                bundle = bundle,
                                onEditExpense = { exp ->
                                    editingExpense = exp
                                    isAddingOrEditingExpense = true
                                },
                                onQuickChangeCategory = { exp, newCat ->
                                    viewModel.updateExpenseCategory(exp, newCat)
                                },
                                onDeleteExpense = { id ->
                                    viewModel.deleteExpense(id)
                                }
                            )

                            MainNavTab.INTELLIGENCE -> IntelligenceHubScreen(
                                selectedTab = intelligenceSubTab,
                                onSelectTab = { intelligenceSubTab = it },
                                bundle = bundle,
                                briefingState = aiBriefingState,
                                advisorState = advisorState,
                                viewModel = viewModel
                            )

                            MainNavTab.BUDGETS -> BudgetPlannerScreen(
                                bundle = bundle,
                                onUpdateOverallTargets = { inc, tgt ->
                                    viewModel.updateProfileSettings(
                                        displayName = bundle.profile.displayName,
                                        currencyCode = bundle.profile.currencyCode,
                                        monthlyIncome = inc,
                                        monthlyBudgetTarget = tgt
                                    )
                                },
                                onUpsertCategoryBudget = { cat, limit, threshold ->
                                    viewModel.upsertBudget(cat, limit, threshold)
                                }
                            )

                            MainNavTab.PROFILE -> ProfileSettingsScreen(
                                bundle = bundle,
                                themeMode = themeMode,
                                isBusy = isBusy,
                                onSelectThemeMode = onSelectThemeMode,
                                onSaveProfile = { name, curr, inc, tgt ->
                                    viewModel.updateProfileSettings(name, curr, inc, tgt)
                                },
                                onLoadDemo = { viewModel.loadDemoDataset() },
                                onClearDemo = { viewModel.clearDemoDataset() },
                                onBuildExportCsv = { viewModel.buildExportCsv(bundle) },
                                onDeleteAllData = { viewModel.deleteAllFinancialData() },
                                onSignOut = {
                                    scope.launch {
                                        withContext(NonCancellable) {
                                            try {
                                                credentialManager.clearCredentialState(
                                                    ClearCredentialStateRequest()
                                                )
                                            } catch (e: Exception) {
                                                Log.w("Auth", "Clear credential failed: ${e.message}")
                                            } finally {
                                                Firebase.auth.signOut()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
