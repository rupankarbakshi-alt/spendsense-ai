package com.example.data.demo

import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import java.util.Calendar

/**
 * Generates clearly-labeled demo transactions (demoRecord = true) spanning 3 prior months
 * plus the current month so users can explore K-Means clustering, Isolation Forest anomaly detection,
 * and OLS linear regression forecasting on demand.
 */
object DemoDataGenerator {

    fun generateDemoExpenses(uid: String): List<Expense> {
        val now = Calendar.getInstance()
        val list = mutableListOf<Expense>()

        fun daysAgo(days: Int, hour: Int = 13): Long {
            return (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -days)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 15)
            }.timeInMillis
        }

        fun monthsAgo(months: Int, dayOfMonth: Int, hour: Int = 12): Long {
            return (now.clone() as Calendar).apply {
                add(Calendar.MONTH, -months)
                val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDay))
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 30)
            }.timeInMillis
        }

        // Current month realistic transactions (kept <= current day of month)
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        fun currentMonthDay(targetOffsetFromToday: Int, hour: Int = 14): Long {
            val safeOffset = targetOffsetFromToday.coerceAtMost((currentDay - 1).coerceAtLeast(0))
            return daysAgo(safeOffset, hour)
        }

        list += Expense(
            userId = uid, title = "Monthly Apartment Maintenance & WiFi", merchant = "Airtel Fiber",
            amount = 2499.0, category = ExpenseCategory.BILLS_UTILITIES.displayName,
            paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = currentMonthDay(12, 10),
            autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "Blue Tokai Specialty Coffee", merchant = "Blue Tokai",
            amount = 380.0, category = ExpenseCategory.FOOD_DINING.displayName,
            paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = currentMonthDay(10, 9),
            autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "Zomato Biryani & Dinner", merchant = "Zomato",
            amount = 740.0, category = ExpenseCategory.FOOD_DINING.displayName,
            paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = currentMonthDay(8, 20),
            autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "Uber Airport & Office Commute", merchant = "Uber",
            amount = 620.0, category = ExpenseCategory.TRANSPORTATION.displayName,
            paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = currentMonthDay(7, 18),
            autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "Zepto Weekly Groceries", merchant = "Zepto",
            amount = 1450.0, category = ExpenseCategory.FOOD_DINING.displayName,
            paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = currentMonthDay(5, 11),
            autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "PVR IMAX Weekend Movie", merchant = "BookMyShow",
            amount = 980.0, category = ExpenseCategory.ENTERTAINMENT.displayName,
            paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = currentMonthDay(4, 19),
            autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "Coursera ML Specialization", merchant = "Coursera",
            amount = 2100.0, category = ExpenseCategory.EDUCATION.displayName,
            paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = currentMonthDay(3, 15),
            autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "Apollo Pharmacy Vitamins", merchant = "Apollo Pharmacy",
            amount = 540.0, category = ExpenseCategory.HEALTHCARE.displayName,
            paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = currentMonthDay(2, 17),
            autoCategorized = true, demoRecord = true
        )
        // Intentional realistic spike / anomaly for Isolation Forest demonstration
        list += Expense(
            userId = uid, title = "Amazon Headphones & Desk Upgrade", merchant = "Amazon",
            amount = 8990.0, category = ExpenseCategory.SHOPPING.displayName,
            paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = currentMonthDay(1, 22),
            notes = "Impulse electronics upgrade", autoCategorized = true, demoRecord = true
        )
        list += Expense(
            userId = uid, title = "Swiggy Lunch Bowl", merchant = "Swiggy",
            amount = 410.0, category = ExpenseCategory.FOOD_DINING.displayName,
            paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = currentMonthDay(0, 13),
            autoCategorized = true, demoRecord = true
        )

        // Prior Month (-1)
        list += listOf(
            Expense(userId = uid, title = "Electricity & Fiber Bill", merchant = "BESCOM", amount = 2350.0, category = ExpenseCategory.BILLS_UTILITIES.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(1, 4, 10), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Groceries & Produce", merchant = "BigBasket", amount = 2100.0, category = ExpenseCategory.FOOD_DINING.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(1, 8, 11), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Metro Smart Card Top-up", merchant = "Metro", amount = 800.0, category = ExpenseCategory.TRANSPORTATION.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(1, 11, 9), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Myntra Casual Sneakers", merchant = "Myntra", amount = 2690.0, category = ExpenseCategory.SHOPPING.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(1, 15, 20), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Zomato Weekend Pizza", merchant = "Zomato", amount = 820.0, category = ExpenseCategory.FOOD_DINING.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(1, 18, 21), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Netflix & Spotify Bundle", merchant = "Netflix", amount = 768.0, category = ExpenseCategory.ENTERTAINMENT.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(1, 21, 8), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Weekend Coorg Homestay", merchant = "Airbnb", amount = 5400.0, category = ExpenseCategory.TRAVEL.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(1, 24, 16), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Uber Rides", merchant = "Uber", amount = 950.0, category = ExpenseCategory.TRANSPORTATION.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(1, 26, 19), autoCategorized = true, demoRecord = true)
        )

        // Prior Month (-2)
        list += listOf(
            Expense(userId = uid, title = "Electricity & Broadband", merchant = "Airtel", amount = 2280.0, category = ExpenseCategory.BILLS_UTILITIES.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(2, 5, 10), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Swiggy & Blinkit Orders", merchant = "Swiggy", amount = 2850.0, category = ExpenseCategory.FOOD_DINING.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(2, 12, 19), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Zara Apparel", merchant = "Zara", amount = 3400.0, category = ExpenseCategory.SHOPPING.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(2, 16, 17), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Fuel & Fastag", merchant = "Shell", amount = 1850.0, category = ExpenseCategory.TRANSPORTATION.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(2, 20, 14), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Dental Checkup", merchant = "Clove Dental", amount = 1500.0, category = ExpenseCategory.HEALTHCARE.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(2, 23, 11), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Technical Reference Books", merchant = "Amazon Books", amount = 1420.0, category = ExpenseCategory.EDUCATION.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(2, 27, 15), autoCategorized = true, demoRecord = true)
        )

        // Prior Month (-3)
        list += listOf(
            Expense(userId = uid, title = "Utility & Fiber Bundle", merchant = "BESCOM", amount = 2150.0, category = ExpenseCategory.BILLS_UTILITIES.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(3, 6, 10), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Monthly Groceries", merchant = "BigBasket", amount = 2400.0, category = ExpenseCategory.FOOD_DINING.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(3, 11, 12), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "IndiGo Domestic Flight", merchant = "IndiGo", amount = 4800.0, category = ExpenseCategory.TRAVEL.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(3, 17, 9), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Streaming & Cinema", merchant = "Netflix", amount = 1120.0, category = ExpenseCategory.ENTERTAINMENT.displayName, paymentMethod = PaymentMethod.CARD.displayName, dateEpochMillis = monthsAgo(3, 22, 20), autoCategorized = true, demoRecord = true),
            Expense(userId = uid, title = "Commute & Cabs", merchant = "Ola", amount = 1180.0, category = ExpenseCategory.TRANSPORTATION.displayName, paymentMethod = PaymentMethod.UPI.displayName, dateEpochMillis = monthsAgo(3, 25, 18), autoCategorized = true, demoRecord = true)
        )

        return list
    }

    fun generateDemoBudgets(uid: String): List<CategoryBudget> = listOf(
        CategoryBudget(userId = uid, category = ExpenseCategory.FOOD_DINING.displayName, monthlyLimit = 5000.0, alertThresholdPct = 80L),
        CategoryBudget(userId = uid, category = ExpenseCategory.SHOPPING.displayName, monthlyLimit = 4500.0, alertThresholdPct = 80L),
        CategoryBudget(userId = uid, category = ExpenseCategory.TRANSPORTATION.displayName, monthlyLimit = 2500.0, alertThresholdPct = 85L),
        CategoryBudget(userId = uid, category = ExpenseCategory.BILLS_UTILITIES.displayName, monthlyLimit = 3500.0, alertThresholdPct = 90L),
        CategoryBudget(userId = uid, category = ExpenseCategory.ENTERTAINMENT.displayName, monthlyLimit = 2000.0, alertThresholdPct = 80L),
        CategoryBudget(userId = uid, category = ExpenseCategory.EDUCATION.displayName, monthlyLimit = 3000.0, alertThresholdPct = 85L),
        CategoryBudget(userId = uid, category = ExpenseCategory.HEALTHCARE.displayName, monthlyLimit = 2000.0, alertThresholdPct = 80L),
        CategoryBudget(userId = uid, category = ExpenseCategory.TRAVEL.displayName, monthlyLimit = 4000.0, alertThresholdPct = 80L)
    )
}
