package com.example.ml

import com.example.data.model.CategoryBudget
import com.example.data.model.CurrencyOption
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.UserProfile
import java.util.Calendar
import kotlin.math.abs

data class CategoryBreakdownItem(
    val category: ExpenseCategory,
    val amount: Double,
    val previousMonthAmount: Double,
    val percentageOfTotal: Double,
    val momDeltaPct: Double?
)

data class BudgetStatusItem(
    val category: ExpenseCategory,
    val spent: Double,
    val monthlyLimit: Double,
    val alertThresholdPct: Long,
    val utilizationRatio: Double,
    val isOverBudget: Boolean,
    val isNearThreshold: Boolean
)

data class BehavioralInsight(
    val title: String,
    val message: String,
    val type: InsightType
)

enum class InsightType {
    POSITIVE, WARNING, ANOMALY, INFO
}

data class SpendIntelligenceSnapshot(
    val currency: CurrencyOption,
    val totalMonthlyExpense: Double,
    val previousMonthExpense: Double,
    val totalIncome: Double,
    val monthlyBudgetTarget: Double,
    val remainingBalance: Double,
    val monthlySavings: Double,
    val savingsRatePct: Double,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val dailySpendSeries: List<Pair<Int, Double>>,
    val budgetStatuses: List<BudgetStatusItem>,
    val isolationForestReport: IsolationForestReport,
    val kMeansReport: KMeansReport,
    val forecastReport: ExpenseForecastReport,
    val insights: List<BehavioralInsight>,
    val budgetRecommendations: List<String>,
    val recentTransactions: List<Expense>,
    val anomalyExpenseIds: Set<String>
)

object MlAnalyticsEngine {

    fun analyze(
        profile: UserProfile,
        expenses: List<Expense>,
        budgets: List<CategoryBudget>,
        nowMillis: Long = System.currentTimeMillis()
    ): SpendIntelligenceSnapshot {
        val currency = CurrencyOption.fromCode(profile.currencyCode)
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH)
        val currentMonthKey = currentYear * 100 + currentMonth

        val prevCal = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val prevMonthKey = prevCal.get(Calendar.YEAR) * 100 + prevCal.get(Calendar.MONTH)

        val currentMonthExpenses = expenses.filter { monthKey(it.dateEpochMillis) == currentMonthKey }
        val prevMonthExpenses = expenses.filter { monthKey(it.dateEpochMillis) == prevMonthKey }

        val totalMonthlyExpense = currentMonthExpenses.sumOf { it.amount }
        val previousMonthExpense = prevMonthExpenses.sumOf { it.amount }
        val totalIncome = profile.monthlyIncome.coerceAtLeast(0.0)
        val remainingBalance = totalIncome - totalMonthlyExpense
        val monthlySavings = remainingBalance.coerceAtLeast(0.0)
        val savingsRatePct = if (totalIncome > 0.0) (remainingBalance / totalIncome) * 100.0 else 0.0

        // Category breakdown (current month vs previous month)
        val currentByCat = currentMonthExpenses.groupBy { it.resolvedCategory() }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val prevByCat = prevMonthExpenses.groupBy { it.resolvedCategory() }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val breakdown = currentByCat.entries
            .sortedByDescending { it.value }
            .map { (cat, amt) ->
                val prevAmt = prevByCat[cat] ?: 0.0
                val mom = if (prevAmt > 0.0) ((amt - prevAmt) / prevAmt) * 100.0 else null
                CategoryBreakdownItem(
                    category = cat,
                    amount = amt,
                    previousMonthAmount = prevAmt,
                    percentageOfTotal = if (totalMonthlyExpense > 0.0) (amt / totalMonthlyExpense) * 100.0 else 0.0,
                    momDeltaPct = mom
                )
            }

        // Daily series for current month
        val maxDay = nowCal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val dailyTotals = (1..maxDay).associateWith { 0.0 }.toMutableMap()
        for (exp in currentMonthExpenses) {
            val c = Calendar.getInstance().apply { timeInMillis = exp.dateEpochMillis }
            val d = c.get(Calendar.DAY_OF_MONTH)
            if (d in 1..maxDay) {
                dailyTotals[d] = (dailyTotals[d] ?: 0.0) + exp.amount
            }
        }
        val dailySeries = dailyTotals.entries.sortedBy { it.key }.map { it.key to it.value }

        // Budget statuses
        val budgetStatuses = budgets
            .filter { it.monthlyLimit > 0.0 }
            .map { b ->
                val cat = b.resolvedCategory()
                val spent = currentByCat[cat] ?: 0.0
                val ratio = spent / b.monthlyLimit
                BudgetStatusItem(
                    category = cat,
                    spent = spent,
                    monthlyLimit = b.monthlyLimit,
                    alertThresholdPct = b.alertThresholdPct,
                    utilizationRatio = ratio,
                    isOverBudget = spent > b.monthlyLimit,
                    isNearThreshold = ratio * 100.0 >= b.alertThresholdPct && spent <= b.monthlyLimit
                )
            }
            .sortedByDescending { it.utilizationRatio }

        // Run ML pipelines
        val isolationReport = IsolationForestDetector.detect(expenses)
        val kMeansReport = KMeansClusterer.cluster(expenses)
        val forecastReport = ExpenseForecaster.forecast(expenses, budgets, nowMillis)

        // Generate data-grounded behavioral insights
        val insights = buildInsights(
            currency = currency,
            profile = profile,
            totalMonthlyExpense = totalMonthlyExpense,
            previousMonthExpense = previousMonthExpense,
            breakdown = breakdown,
            isolationReport = isolationReport,
            forecastReport = forecastReport,
            currentMonthExpenses = currentMonthExpenses
        )

        // Generate data-driven budget recommendations
        val recommendations = buildRecommendations(
            currency = currency,
            profile = profile,
            totalMonthlyExpense = totalMonthlyExpense,
            breakdown = breakdown,
            budgetStatuses = budgetStatuses,
            forecastReport = forecastReport,
            isolationReport = isolationReport
        )

        val recent = expenses.sortedByDescending { it.dateEpochMillis }.take(5)
        val anomalyIds = isolationReport.flaggedTransactions.map { it.expense.id }.filter { it.isNotBlank() }.toSet()

        return SpendIntelligenceSnapshot(
            currency = currency,
            totalMonthlyExpense = totalMonthlyExpense,
            previousMonthExpense = previousMonthExpense,
            totalIncome = totalIncome,
            monthlyBudgetTarget = profile.monthlyBudgetTarget,
            remainingBalance = remainingBalance,
            monthlySavings = monthlySavings,
            savingsRatePct = savingsRatePct,
            categoryBreakdown = breakdown,
            dailySpendSeries = dailySeries,
            budgetStatuses = budgetStatuses,
            isolationForestReport = isolationReport,
            kMeansReport = kMeansReport,
            forecastReport = forecastReport,
            insights = insights,
            budgetRecommendations = recommendations,
            recentTransactions = recent,
            anomalyExpenseIds = anomalyIds
        )
    }

    private fun buildInsights(
        currency: CurrencyOption,
        profile: UserProfile,
        totalMonthlyExpense: Double,
        previousMonthExpense: Double,
        breakdown: List<CategoryBreakdownItem>,
        isolationReport: IsolationForestReport,
        forecastReport: ExpenseForecastReport,
        currentMonthExpenses: List<Expense>
    ): List<BehavioralInsight> {
        val list = mutableListOf<BehavioralInsight>()

        if (currentMonthExpenses.isEmpty()) {
            list.add(
                BehavioralInsight(
                    title = "Ready to Learn Your Habits",
                    message = "Add your first expenses this month or load the labeled Demo Mode to explore ML pattern detection.",
                    type = InsightType.INFO
                )
            )
            return list
        }

        val topCat = breakdown.firstOrNull()
        if (topCat != null) {
            list.add(
                BehavioralInsight(
                    title = "Top Spending Driver: ${topCat.category.displayName}",
                    message = "${topCat.category.displayName} accounts for ${topCat.percentageOfTotal.toInt()}% " +
                        "(${currency.format(topCat.amount)}) of your spending this month.",
                    type = InsightType.INFO
                )
            )
        }

        // MoM category comparisons
        breakdown.firstOrNull { (it.momDeltaPct ?: 0.0) >= 15.0 }?.let { rising ->
            list.add(
                BehavioralInsight(
                    title = "${rising.category.displayName} Up ${rising.momDeltaPct!!.toInt()}%",
                    message = "You spent ${rising.momDeltaPct.toInt()}% more on ${rising.category.displayName} this month compared to last month.",
                    type = InsightType.WARNING
                )
            )
        }

        breakdown.firstOrNull { (it.momDeltaPct ?: 0.0) <= -15.0 }?.let { saving ->
            list.add(
                BehavioralInsight(
                    title = "${saving.category.displayName} Down ${abs(saving.momDeltaPct!!.toInt())}%",
                    message = "Great discipline: your ${saving.category.displayName} spend dropped by ${abs(saving.momDeltaPct.toInt())}% vs last month.",
                    type = InsightType.POSITIVE
                )
            )
        }

        if (previousMonthExpense > 0.0) {
            val overallDelta = ((totalMonthlyExpense - previousMonthExpense) / previousMonthExpense) * 100.0
            if (overallDelta > 20.0) {
                list.add(
                    BehavioralInsight(
                        title = "Overall Pace Rising",
                        message = "Total spend is up ${overallDelta.toInt()}% compared to last month's ${currency.format(previousMonthExpense)}.",
                        type = InsightType.WARNING
                    )
                )
            }
        }

        isolationReport.flaggedTransactions.firstOrNull()?.let { topAnomaly ->
            list.add(
                BehavioralInsight(
                    title = "Unusual Activity Flagged",
                    message = "${topAnomaly.expense.title} (${currency.format(topAnomaly.expense.amount)}): ${topAnomaly.reason}.",
                    type = InsightType.ANOMALY
                )
            )
        }

        if (profile.monthlyBudgetTarget > 0.0 && forecastReport.predictedMonthlyTotal > profile.monthlyBudgetTarget) {
            val over = forecastReport.predictedMonthlyTotal - profile.monthlyBudgetTarget
            list.add(
                BehavioralInsight(
                    title = "Month-End Budget Alert",
                    message = "Estimated expenses of ${currency.format(forecastReport.predictedMonthlyTotal)} " +
                        "may exceed your ${currency.format(profile.monthlyBudgetTarget)} target by ${currency.format(over)}.",
                    type = InsightType.WARNING
                )
            )
        }

        return list
    }

    private fun buildRecommendations(
        currency: CurrencyOption,
        profile: UserProfile,
        totalMonthlyExpense: Double,
        breakdown: List<CategoryBreakdownItem>,
        budgetStatuses: List<BudgetStatusItem>,
        forecastReport: ExpenseForecastReport,
        isolationReport: IsolationForestReport
    ): List<String> {
        if (totalMonthlyExpense <= 0.0) {
            return listOf(
                "Start recording your daily expenses to unlock personalized savings targets.",
                "Set category caps in Budget Planner so SpendSense AI can track early threshold alerts."
            )
        }

        val recs = mutableListOf<String>()

        if (profile.monthlyBudgetTarget > 0.0 && forecastReport.predictedMonthlyTotal > profile.monthlyBudgetTarget) {
            val excess = forecastReport.predictedMonthlyTotal - profile.monthlyBudgetTarget
            recs.add(
                "You are likely to exceed your monthly budget of ${currency.format(profile.monthlyBudgetTarget)} by ~${currency.format(excess)}. Slow discretionary outlays this week."
            )
        }

        budgetStatuses.filter { it.isOverBudget }.forEach { over ->
            recs.add(
                "Your ${over.category.displayName} spend (${currency.format(over.spent)}) has exceeded its ${currency.format(over.monthlyLimit)} cap."
            )
        }

        breakdown.firstOrNull { it.category == ExpenseCategory.SHOPPING && it.percentageOfTotal >= 20.0 }?.let { shop ->
            recs.add(
                "Consider reducing unnecessary shopping expenses — Shopping currently takes ${shop.percentageOfTotal.toInt()}% (${currency.format(shop.amount)}) of your monthly spend."
            )
        }

        breakdown.firstOrNull { it.category == ExpenseCategory.ENTERTAINMENT && it.amount > 0.0 }?.let { ent ->
            val weeklyCap = (ent.amount * 0.8) / 4.0
            recs.add(
                "You could save more by setting a weekly Entertainment budget around ${currency.format(weeklyCap)}."
            )
        }

        breakdown.firstOrNull { it.category == ExpenseCategory.FOOD_DINING && (it.momDeltaPct ?: 0.0) > 10.0 }?.let { food ->
            recs.add(
                "Your Food & Dining expenses increased ${food.momDeltaPct!!.toInt()}% this month (${currency.format(food.amount)}). Trimming 2 delivery orders/week would save ~${currency.format(food.amount * 0.18)}."
            )
        }

        if (isolationReport.categorySpikes.isNotEmpty()) {
            recs.add(isolationReport.categorySpikes.first())
        }

        if (profile.monthlyIncome > 0.0) {
            val projectedSavingsRate = ((profile.monthlyIncome - forecastReport.predictedMonthlyTotal) / profile.monthlyIncome) * 100.0
            if (projectedSavingsRate >= 20.0) {
                recs.add(
                    "On track to save ~${projectedSavingsRate.toInt()}% of your monthly income (${currency.format(profile.monthlyIncome - forecastReport.predictedMonthlyTotal)}). Consider automating that surplus into investments."
                )
            } else if (projectedSavingsRate in 0.0..19.9) {
                recs.add(
                    "Projected savings rate is ${projectedSavingsRate.toInt()}%. Aim for a 20% buffer (${currency.format(profile.monthlyIncome * 0.2)}) by capping your top variable category."
                )
            }
        }

        return recs.distinct()
    }

    private fun monthKey(epochMillis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
    }
}
