package com.example.ml

import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

data class MonthlyPoint(
    val monthKey: Int,
    val label: String,
    val actualTotal: Double,
    val isForecast: Boolean = false
)

data class CategoryForecast(
    val category: ExpenseCategory,
    val actualToDate: Double,
    val predictedMonthlyTotal: Double,
    val budgetLimit: Double
)

data class ExpenseForecastReport(
    val modelTrained: Boolean,
    val isInitialEstimate: Boolean,
    val monthsOfHistory: Int,
    val actualCurrentMonthSpend: Double,
    val predictedMonthlyTotal: Double,
    val confidenceLow: Double,
    val confidenceHigh: Double,
    val rSquared: Double?,
    val monthlyTrendSlope: Double?,
    val monthlySeries: List<MonthlyPoint>,
    val categoryForecasts: List<CategoryForecast>,
    val modelExplanation: String
)

/**
 * Combines Ordinary Least Squares (OLS) Linear Regression across historical monthly totals
 * with current-month daily burn-rate run-rate forecasting & standard error confidence intervals.
 */
object ExpenseForecaster {

    fun forecast(
        expenses: List<Expense>,
        budgets: List<CategoryBudget>,
        nowMillis: Long = System.currentTimeMillis()
    ): ExpenseForecastReport {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH)
        val currentMonthKey = currentYear * 100 + currentMonth
        val dayOfMonth = nowCal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val daysInMonth = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)

        val monthFormatter = SimpleDateFormat("MMM", Locale.getDefault())

        // Group expenses by month key (YYYYMM)
        val byMonth = expenses.groupBy { exp ->
            val c = Calendar.getInstance().apply { timeInMillis = exp.dateEpochMillis }
            c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
        }.toSortedMap()

        val currentMonthExpenses = byMonth[currentMonthKey].orEmpty()
        val actualCurrentSpend = currentMonthExpenses.sumOf { it.amount }

        val historicalMonths = byMonth.filterKeys { it < currentMonthKey }
        val historicalTotals = historicalMonths.entries.map { (key, items) ->
            val y = key / 100
            val m = key % 100
            val c = Calendar.getInstance().apply { set(y, m, 1) }
            MonthlyPoint(
                monthKey = key,
                label = monthFormatter.format(c.time),
                actualTotal = items.sumOf { it.amount },
                isForecast = false
            )
        }

        // Daily spending distribution for the current month to compute burn rate & variance
        val dailySpend = DoubleArray(dayOfMonth)
        for (exp in currentMonthExpenses) {
            val c = Calendar.getInstance().apply { timeInMillis = exp.dateEpochMillis }
            val d = (c.get(Calendar.DAY_OF_MONTH) - 1).coerceIn(0, dayOfMonth - 1)
            dailySpend[d] += exp.amount
        }
        val avgDailyBurn = if (dayOfMonth > 0) actualCurrentSpend / dayOfMonth else 0.0
        val dailyVariance = if (dayOfMonth > 1) {
            dailySpend.map { (it - avgDailyBurn) * (it - avgDailyBurn) }.average()
        } else {
            (avgDailyBurn * 0.25) * (avgDailyBurn * 0.25)
        }
        val dailyStd = sqrt(dailyVariance)
        val remainingDays = (daysInMonth - dayOfMonth).coerceAtLeast(0)
        val runRatePrediction = actualCurrentSpend + avgDailyBurn * remainingDays

        val hasFullRegressionHistory = historicalTotals.size >= 2
        var rSquared: Double? = null
        var slope: Double? = null

        val predictedTotal: Double
        val margin: Double
        val explanation: String

        if (expenses.isEmpty()) {
            return ExpenseForecastReport(
                modelTrained = false,
                isInitialEstimate = true,
                monthsOfHistory = 0,
                actualCurrentMonthSpend = 0.0,
                predictedMonthlyTotal = 0.0,
                confidenceLow = 0.0,
                confidenceHigh = 0.0,
                rSquared = null,
                monthlyTrendSlope = null,
                monthlySeries = emptyList(),
                categoryForecasts = emptyList(),
                modelExplanation = "Insufficient transaction history. Add expenses across at least 2 months to train the OLS linear regression forecaster."
            )
        } else if (hasFullRegressionHistory) {
            // Fit Ordinary Least Squares (OLS) regression y = a + b*x over historical months
            val n = historicalTotals.size
            val xs = (0 until n).map { it.toDouble() }
            val ys = historicalTotals.map { it.actualTotal }
            val xMean = xs.average()
            val yMean = ys.average()

            var num = 0.0
            var den = 0.0
            for (i in 0 until n) {
                num += (xs[i] - xMean) * (ys[i] - yMean)
                den += (xs[i] - xMean) * (xs[i] - xMean)
            }
            val b = if (den > 1e-9) num / den else 0.0
            val a = yMean - b * xMean
            slope = b

            val olsCurrentMonth = (a + b * n).coerceAtLeast(0.0)
            // Compute R^2 goodness of fit
            val ssTot = ys.sumOf { (it - yMean) * (it - yMean) }
            val ssRes = (0 until n).sumOf { i ->
                val pred = a + b * xs[i]
                (ys[i] - pred) * (ys[i] - pred)
            }
            rSquared = if (ssTot > 1e-6) (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0) else 1.0

            // Blend OLS trend with observed month-to-date run rate weighted by month progress
            val monthProgress = (dayOfMonth.toDouble() / daysInMonth.toDouble()).coerceIn(0.15, 0.90)
            predictedTotal = max(
                actualCurrentSpend,
                monthProgress * runRatePrediction + (1.0 - monthProgress) * olsCurrentMonth
            )

            val residualStd = sqrt((ssRes / (n - 1).coerceAtLeast(1)).coerceAtLeast(0.0))
            val runRateUncertainty = 1.28 * dailyStd * sqrt(remainingDays.toDouble())
            margin = max(runRateUncertainty, residualStd * 0.65).coerceAtLeast(predictedTotal * 0.06)

            explanation = "Trained OLS Linear Regression across ${historicalTotals.size} prior months " +
                "(R² = ${"%.2f".format(rSquared)}) blended with current daily burn-rate."
        } else {
            predictedTotal = max(actualCurrentSpend, runRatePrediction)
            margin = (1.28 * dailyStd * sqrt(remainingDays.toDouble())).coerceAtLeast(predictedTotal * 0.12)
            explanation = "Initial Run-Rate Estimate: only ${historicalTotals.size} prior month(s) recorded. " +
                "Using current daily burn rate (${"%.0f".format(avgDailyBurn)}/day) until ≥2 months of history are available for OLS regression."
        }

        val low = max(actualCurrentSpend, predictedTotal - margin)
        val high = predictedTotal + margin

        // Category-wise predicted vs actual
        val budgetByCat = budgets.associateBy { ExpenseCategory.fromDisplayName(it.category) }
        val categoryForecasts = ExpenseCategory.entries.mapNotNull { cat ->
            val catCurrent = currentMonthExpenses.filter { it.resolvedCategory() == cat }.sumOf { it.amount }
            val catHistAvg = historicalMonths.values.map { monthList ->
                monthList.filter { it.resolvedCategory() == cat }.sumOf { it.amount }
            }.filter { it > 0.0 }.let { if (it.isEmpty()) 0.0 else it.average() }

            val budgetCap = budgetByCat[cat]?.monthlyLimit ?: 0.0
            if (catCurrent == 0.0 && catHistAvg == 0.0 && budgetCap == 0.0) {
                return@mapNotNull null
            }

            val catRunRate = catCurrent + (catCurrent / dayOfMonth) * remainingDays
            val progress = (dayOfMonth.toDouble() / daysInMonth.toDouble()).coerceIn(0.15, 0.90)
            val catPredicted = if (catHistAvg > 0.0) {
                max(catCurrent, progress * catRunRate + (1.0 - progress) * catHistAvg)
            } else {
                max(catCurrent, catRunRate)
            }

            CategoryForecast(
                category = cat,
                actualToDate = catCurrent,
                predictedMonthlyTotal = catPredicted,
                budgetLimit = budgetCap
            )
        }.sortedByDescending { it.predictedMonthlyTotal }

        // Build Monthly Series (last 5 historical + current month + projected current month)
        val currentLabel = monthFormatter.format(nowCal.time)
        val series = historicalTotals.takeLast(5) + listOf(
            MonthlyPoint(
                monthKey = currentMonthKey,
                label = "$currentLabel (MTD)",
                actualTotal = actualCurrentSpend,
                isForecast = false
            ),
            MonthlyPoint(
                monthKey = currentMonthKey + 1,
                label = "$currentLabel (Est)",
                actualTotal = predictedTotal,
                isForecast = true
            )
        )

        return ExpenseForecastReport(
            modelTrained = hasFullRegressionHistory,
            isInitialEstimate = !hasFullRegressionHistory,
            monthsOfHistory = historicalTotals.size,
            actualCurrentMonthSpend = actualCurrentSpend,
            predictedMonthlyTotal = predictedTotal,
            confidenceLow = low,
            confidenceHigh = high,
            rSquared = rSquared,
            monthlyTrendSlope = slope,
            monthlySeries = series,
            categoryForecasts = categoryForecasts,
            modelExplanation = explanation
        )
    }
}
