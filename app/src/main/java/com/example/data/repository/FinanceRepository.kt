package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.demo.DemoDataGenerator
import com.example.data.model.CategoryBudget
import com.example.data.model.CategoryRule
import com.example.data.model.CurrencyOption
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeExpenses(userId: String): Flow<List<Expense>> = flow {
        val path = "users/$userId/expenses"
        emitAll(
            db.collection("users").document(userId).collection("expenses")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Expense::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.dateEpochMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBudgets(userId: String): Flow<List<CategoryBudget>> = flow {
        val path = "users/$userId/budgets"
        emitAll(
            db.collection("users").document(userId).collection("budgets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CategoryBudget::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCategoryRules(userId: String): Flow<List<CategoryRule>> = flow {
        val path = "users/$userId/category_rules"
        emitAll(
            db.collection("users").document(userId).collection("category_rules")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CategoryRule::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfileExists(
        displayName: String,
        email: String
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val initialProfile = UserProfile(
                    userId = uid,
                    displayName = displayName.ifBlank { "SpendSense User" },
                    email = email,
                    currencyCode = "INR",
                    monthlyIncome = 50000.0,
                    monthlyBudgetTarget = 25000.0,
                    demoModeActive = false
                )
                docRef.set(initialProfile.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        currencyCode: String,
        monthlyIncome: Double,
        monthlyBudgetTarget: Double
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
            require(monthlyIncome in 0.0..100_000_000.0) { "Income must be between 0 and 100,000,000." }
            require(monthlyBudgetTarget in 0.0..100_000_000.0) { "Budget target must be between 0 and 100,000,000." }

            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "displayName" to cleanName,
                    "currencyCode" to CurrencyOption.fromCode(currencyCode).code,
                    "monthlyIncome" to monthlyIncome,
                    "monthlyBudgetTarget" to monthlyBudgetTarget,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addExpense(expense: Expense): Result<Unit> {
        var docPath = "users/unknown/expenses"
        return try {
            val uid = requireUserId()
            require(expense.title.trim().isNotEmpty()) { "Title cannot be empty." }
            require(expense.amount > 0.0 && expense.amount <= 10_000_000.0) {
                "Amount must be greater than 0."
            }
            val docRef = db.collection("users").document(uid).collection("expenses").document()
            docPath = docRef.path
            docRef.set(expense.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateExpense(expense: Expense): Result<Unit> {
        var docPath = "users/unknown/expenses/${expense.id}"
        return try {
            val uid = requireUserId()
            require(expense.id.isNotBlank()) { "Expense ID cannot be blank." }
            require(expense.title.trim().isNotEmpty()) { "Title cannot be empty." }
            require(expense.amount > 0.0 && expense.amount <= 10_000_000.0) {
                "Amount must be greater than 0."
            }
            val docRef = db.collection("users").document(uid).collection("expenses").document(expense.id)
            docPath = docRef.path
            docRef.update(expense.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(expenseId: String): Result<Unit> {
        var docPath = "users/unknown/expenses/$expenseId"
        return try {
            val uid = requireUserId()
            require(expenseId.isNotBlank()) { "Expense ID cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("expenses").document(expenseId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCategoryRule(keyword: String, category: ExpenseCategory): Result<Unit> {
        var docPath = "users/unknown/category_rules"
        return try {
            val uid = requireUserId()
            val cleanKeyword = keyword.trim().lowercase().take(60)
            if (cleanKeyword.isBlank()) return Result.success(Unit)
            val ruleId = "rule_" + cleanKeyword.replace(Regex("[^a-z0-9]+"), "_").trim('_').take(60)
                .ifBlank { "custom" }
            val docRef = db.collection("users").document(uid)
                .collection("category_rules").document(ruleId)
            docPath = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "keyword" to cleanKeyword,
                        "category" to category.displayName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val rule = CategoryRule(
                    userId = uid,
                    keyword = cleanKeyword,
                    category = category.displayName
                )
                docRef.set(rule.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertBudget(
        category: ExpenseCategory,
        monthlyLimit: Double,
        alertThresholdPct: Long
    ): Result<Unit> {
        var docPath = "users/unknown/budgets/${category.slug}"
        return try {
            val uid = requireUserId()
            require(monthlyLimit in 0.0..10_000_000.0) { "Limit must be between 0 and 10,000,000." }
            val clampedThreshold = alertThresholdPct.coerceIn(50L, 100L)
            val docRef = db.collection("users").document(uid)
                .collection("budgets").document(category.slug)
            docPath = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "category" to category.displayName,
                        "monthlyLimit" to monthlyLimit,
                        "alertThresholdPct" to clampedThreshold,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val budget = CategoryBudget(
                    userId = uid,
                    category = category.displayName,
                    monthlyLimit = monthlyLimit,
                    alertThresholdPct = clampedThreshold
                )
                docRef.set(budget.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun loadDemoDataset(): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            docPath = userDoc.path

            val demoExpenses = DemoDataGenerator.generateDemoExpenses(uid)
            val demoBudgets = DemoDataGenerator.generateDemoBudgets(uid)

            val batch = db.batch()
            batch.update(
                userDoc,
                mapOf(
                    "monthlyIncome" to 65000.0,
                    "monthlyBudgetTarget" to 26000.0,
                    "demoModeActive" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            demoExpenses.forEach { expense ->
                val ref = userDoc.collection("expenses").document()
                batch.set(ref, expense.toCreateMap(uid))
            }
            demoBudgets.forEach { budget ->
                val cat = ExpenseCategory.fromDisplayName(budget.category)
                val ref = userDoc.collection("budgets").document(cat.slug)
                batch.set(ref, budget.toCreateMap(uid))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearDemoDataset(): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            docPath = userDoc.path
            val demoSnap = userDoc.collection("expenses")
                .whereEqualTo("demoRecord", true)
                .get()
                .await()

            val batch = db.batch()
            demoSnap.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.update(
                userDoc,
                mapOf(
                    "demoModeActive" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            docPath = userDoc.path

            val expensesSnap = userDoc.collection("expenses").get().await()
            val budgetsSnap = userDoc.collection("budgets").get().await()
            val rulesSnap = userDoc.collection("category_rules").get().await()

            val allRefs = expensesSnap.documents.map { it.reference } +
                budgetsSnap.documents.map { it.reference } +
                rulesSnap.documents.map { it.reference }

            // Commit in chunks of 400 to respect Firestore 500-op batch limit
            allRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                batch.commit().await()
            }

            userDoc.update(
                mapOf(
                    "demoModeActive" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun buildExportCsv(profile: UserProfile?, expenses: List<Expense>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("SpendSense AI Financial Export")
        sb.appendLine("User,${escapeCsv(profile?.displayName ?: "User")},Currency,${profile?.currencyCode ?: "INR"}")
        sb.appendLine("Date,Title,Merchant,Category,Amount,PaymentMethod,DemoRecord,Notes")
        expenses.sortedByDescending { it.dateEpochMillis }.forEach { exp ->
            val dateStr = dateFormat.format(Date(exp.dateEpochMillis))
            sb.appendLine(
                listOf(
                    escapeCsv(dateStr),
                    escapeCsv(exp.title),
                    escapeCsv(exp.merchant),
                    escapeCsv(exp.category),
                    exp.amount.toString(),
                    escapeCsv(exp.paymentMethod),
                    exp.demoRecord.toString(),
                    escapeCsv(exp.notes)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }
}
