package com.example

import com.example.data.demo.DemoDataGenerator
import com.example.data.model.CategoryRule
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.UserProfile
import com.example.ml.ExpenseClassifier
import com.example.ml.ExpenseForecaster
import com.example.ml.IsolationForestDetector
import com.example.ml.KMeansClusterer
import com.example.ml.MlAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlAnalyticsEngineTest {

    @Test
    fun `ExpenseClassifier matches built-in keywords and prioritizes user learned rules`() {
        val zomatoResult = ExpenseClassifier.classify(title = "Zomato Biryani", merchant = "Zomato")
        assertEquals(ExpenseCategory.FOOD_DINING, zomatoResult.category)
        assertTrue(zomatoResult.confidence > 0.6)

        val userRules = listOf(
            CategoryRule(userId = "u1", keyword = "zomato", category = ExpenseCategory.ENTERTAINMENT.displayName)
        )
        val overridden = ExpenseClassifier.classify(
            title = "Zomato Party",
            merchant = "Zomato",
            userRules = userRules
        )
        assertEquals(ExpenseCategory.ENTERTAINMENT, overridden.category)
        assertTrue(overridden.confidence >= 0.9)
    }

    @Test
    fun `IsolationForestDetector reports limitation under 8 samples and flags spike on rich dataset`() {
        val smallList = listOf(
            Expense(id = "1", title = "Coffee", amount = 200.0, category = ExpenseCategory.FOOD_DINING.displayName),
            Expense(id = "2", title = "Lunch", amount = 350.0, category = ExpenseCategory.FOOD_DINING.displayName)
        )
        val smallReport = IsolationForestDetector.detect(smallList)
        assertFalse(smallReport.modelTrained)
        assertTrue(smallReport.limitationMessage != null)

        val demoExpenses = DemoDataGenerator.generateDemoExpenses("u1").mapIndexed { idx, e ->
            e.copy(id = "exp_$idx")
        }
        val richReport = IsolationForestDetector.detect(demoExpenses)
        assertTrue(richReport.modelTrained)
        assertTrue(richReport.flaggedTransactions.any { it.expense.title.contains("Headphones") })
    }

    @Test
    fun `KMeansClusterer groups demo expenses into 3 behavioral segments`() {
        val demoExpenses = DemoDataGenerator.generateDemoExpenses("u1")
        val report = KMeansClusterer.cluster(demoExpenses)
        assertTrue(report.modelTrained)
        assertEquals(3, report.clusters.size)
        val totalPct = report.clusters.sumOf { it.sharePercentage }
        assertTrue(totalPct in 99.0..101.0)
    }

    @Test
    fun `ExpenseForecaster trains OLS regression on multi-month demo data and produces confidence interval`() {
        val demoExpenses = DemoDataGenerator.generateDemoExpenses("u1")
        val demoBudgets = DemoDataGenerator.generateDemoBudgets("u1")
        val forecast = ExpenseForecaster.forecast(demoExpenses, demoBudgets)
        assertTrue(forecast.modelTrained)
        assertFalse(forecast.isInitialEstimate)
        assertTrue(forecast.monthsOfHistory >= 2)
        assertTrue(forecast.predictedMonthlyTotal >= forecast.actualCurrentMonthSpend)
        assertTrue(forecast.confidenceHigh >= forecast.confidenceLow)
    }

    @Test
    fun `MlAnalyticsEngine computes accurate dashboard balance savings and recommendations`() {
        val profile = UserProfile(
            userId = "u1",
            displayName = "Test",
            monthlyIncome = 65000.0,
            monthlyBudgetTarget = 26000.0,
            currencyCode = "INR"
        )
        val demoExpenses = DemoDataGenerator.generateDemoExpenses("u1").mapIndexed { idx, e ->
            e.copy(id = "exp_$idx")
        }
        val demoBudgets = DemoDataGenerator.generateDemoBudgets("u1")
        val snapshot = MlAnalyticsEngine.analyze(profile, demoExpenses, demoBudgets)

        assertTrue(snapshot.totalMonthlyExpense > 0.0)
        assertEquals(profile.monthlyIncome - snapshot.totalMonthlyExpense, snapshot.remainingBalance, 0.01)
        assertTrue(snapshot.insights.isNotEmpty())
        assertTrue(snapshot.budgetRecommendations.isNotEmpty())
    }
}
