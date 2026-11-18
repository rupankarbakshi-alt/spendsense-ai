package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.util.Locale

enum class ExpenseCategory(
    val displayName: String,
    val slug: String,
    val icon: ImageVector,
    val chartColor: Color
) {
    FOOD_DINING(
        displayName = "Food & Dining",
        slug = "food_dining",
        icon = Icons.Outlined.Restaurant,
        chartColor = Color(0xFF10B981)
    ),
    SHOPPING(
        displayName = "Shopping",
        slug = "shopping",
        icon = Icons.Outlined.ShoppingBag,
        chartColor = Color(0xFF38BDF8)
    ),
    TRANSPORTATION(
        displayName = "Transportation",
        slug = "transportation",
        icon = Icons.Outlined.DirectionsCar,
        chartColor = Color(0xFFF59E0B)
    ),
    EDUCATION(
        displayName = "Education",
        slug = "education",
        icon = Icons.Outlined.School,
        chartColor = Color(0xFF818CF8)
    ),
    ENTERTAINMENT(
        displayName = "Entertainment",
        slug = "entertainment",
        icon = Icons.Outlined.Movie,
        chartColor = Color(0xFFF472B6)
    ),
    HEALTHCARE(
        displayName = "Healthcare",
        slug = "healthcare",
        icon = Icons.Outlined.LocalHospital,
        chartColor = Color(0xFFFB7185)
    ),
    BILLS_UTILITIES(
        displayName = "Bills & Utilities",
        slug = "bills_utilities",
        icon = Icons.Outlined.ReceiptLong,
        chartColor = Color(0xFF2DD4BF)
    ),
    TRAVEL(
        displayName = "Travel",
        slug = "travel",
        icon = Icons.Outlined.Flight,
        chartColor = Color(0xFFA78BFA)
    ),
    OTHER(
        displayName = "Other",
        slug = "other",
        icon = Icons.Outlined.Category,
        chartColor = Color(0xFF94A3B8)
    );

    companion object {
        fun fromDisplayName(name: String?): ExpenseCategory =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: OTHER
    }
}

enum class PaymentMethod(val displayName: String) {
    UPI("UPI"),
    CARD("Card"),
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer"),
    WALLET("Wallet");

    companion object {
        fun fromDisplayName(name: String?): PaymentMethod =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: UPI
    }
}

enum class CurrencyOption(val code: String, val symbol: String, val label: String) {
    INR("INR", "₹", "Indian Rupee (₹)"),
    USD("USD", "$", "US Dollar ($)"),
    EUR("EUR", "€", "Euro (€)"),
    GBP("GBP", "£", "British Pound (£)");

    fun format(amount: Double): String {
        val locale = when (this) {
            INR -> Locale("en", "IN")
            USD -> Locale.US
            EUR -> Locale.GERMANY
            GBP -> Locale.UK
        }
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = 0
        }
        return "$symbol${formatter.format(amount)}"
    }

    companion object {
        fun fromCode(code: String?): CurrencyOption =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: INR
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val currencyCode: String = "INR",
    val monthlyIncome: Double = 50000.0,
    val monthlyBudgetTarget: Double = 25000.0,
    val demoModeActive: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to displayName.trim().take(60).ifBlank { "SpendSense User" },
        "email" to email.trim().take(120),
        "currencyCode" to CurrencyOption.fromCode(currencyCode).code,
        "monthlyIncome" to monthlyIncome.coerceIn(0.0, 100_000_000.0),
        "monthlyBudgetTarget" to monthlyBudgetTarget.coerceIn(0.0, 100_000_000.0),
        "demoModeActive" to demoModeActive,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val merchant: String = "",
    val amount: Double = 0.0,
    val category: String = ExpenseCategory.OTHER.displayName,
    val paymentMethod: String = PaymentMethod.UPI.displayName,
    val notes: String = "",
    val dateEpochMillis: Long = System.currentTimeMillis(),
    val autoCategorized: Boolean = false,
    val demoRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): ExpenseCategory = ExpenseCategory.fromDisplayName(category)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(100),
        "merchant" to merchant.trim().take(80),
        "amount" to amount.coerceIn(0.01, 10_000_000.0),
        "category" to ExpenseCategory.fromDisplayName(category).displayName,
        "paymentMethod" to PaymentMethod.fromDisplayName(paymentMethod).displayName,
        "notes" to notes.trim().take(300),
        "dateEpochMillis" to dateEpochMillis.coerceAtLeast(1L),
        "autoCategorized" to autoCategorized,
        "demoRecord" to demoRecord,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(100),
        "merchant" to merchant.trim().take(80),
        "amount" to amount.coerceIn(0.01, 10_000_000.0),
        "category" to ExpenseCategory.fromDisplayName(category).displayName,
        "paymentMethod" to PaymentMethod.fromDisplayName(paymentMethod).displayName,
        "notes" to notes.trim().take(300),
        "dateEpochMillis" to dateEpochMillis.coerceAtLeast(1L),
        "autoCategorized" to autoCategorized,
        "demoRecord" to demoRecord,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CategoryBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = ExpenseCategory.OTHER.displayName,
    val monthlyLimit: Double = 0.0,
    val alertThresholdPct: Long = 80L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): ExpenseCategory = ExpenseCategory.fromDisplayName(category)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "category" to ExpenseCategory.fromDisplayName(category).displayName,
        "monthlyLimit" to monthlyLimit.coerceIn(0.0, 10_000_000.0),
        "alertThresholdPct" to alertThresholdPct.coerceIn(50L, 100L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CategoryRule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val keyword: String = "",
    val category: String = ExpenseCategory.OTHER.displayName,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "keyword" to keyword.trim().lowercase().take(60),
        "category" to ExpenseCategory.fromDisplayName(category).displayName,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
