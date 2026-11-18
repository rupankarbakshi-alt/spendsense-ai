package com.example.data.ai

import android.util.Log
import com.example.data.model.ExpenseCategory
import com.example.ml.SpendIntelligenceSnapshot
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiClassificationResponse(
    val category: ExpenseCategory,
    val confidence: Double,
    val reasoning: String
)

data class AiCoachingBriefing(
    val executiveSummary: String,
    val topHabitObservation: String,
    val actionSteps: List<String>,
    val riskFlag: String
)

class GeminiAiService {

    companion object {
        private const val TAG = "GeminiAiService"
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val TIMEOUT_MS = 60_000L
    }

    private val classificationSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "category" to Schema.enumeration(
                    values = ExpenseCategory.entries.map { it.displayName },
                    description = "Best matching expense category from the allowed list"
                ),
                "confidence" to Schema.double(
                    description = "Classification confidence from 0.0 to 1.0"
                ),
                "reasoning" to Schema.string(
                    description = "Concise one-sentence justification"
                )
            )
        )
    }

    private val coachingSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "executiveSummary" to Schema.string(
                    description = "2-sentence crisp financial health summary referencing exact numbers"
                ),
                "topHabitObservation" to Schema.string(
                    description = "Key behavioral pattern discovered from the user's spending"
                ),
                "actionSteps" to Schema.array(
                    items = Schema.string(),
                    description = "3 concrete, realistic budget actions tailored to this user's numbers"
                ),
                "riskFlag" to Schema.string(
                    description = "Primary overspend or anomaly risk to watch this month"
                )
            )
        )
    }

    private val classifierModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = classificationSchema
            }
        )
    }

    private val coachingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = coachingSchema
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = MODEL_NAME)
    }

    suspend fun classifyExpense(
        title: String,
        merchant: String,
        notes: String,
        amount: Double?
    ): Result<AiClassificationResponse> {
        return try {
            val prompt = """
                Categorize this personal expense transaction into one of the exact 9 SpendSense categories.
                Title: "$title"
                Merchant: "$merchant"
                Notes: "$notes"
                Amount: ${amount ?: "unspecified"}
            """.trimIndent()

            val response = withTimeout(TIMEOUT_MS) {
                classifierModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty classification response.")
            val json = JSONObject(jsonText)
            val category = ExpenseCategory.fromDisplayName(json.optString("category"))
            val confidence = json.optDouble("confidence", 0.85).coerceIn(0.0, 1.0)
            val reasoning = json.optString("reasoning", "Classified by Gemini AI")
            Result.success(AiClassificationResponse(category, confidence, reasoning))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI classification timed out", e)
            Result.failure(Exception("AI classification timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI classification failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateCoachingBriefing(
        snapshot: SpendIntelligenceSnapshot
    ): Result<AiCoachingBriefing> {
        return try {
            val currency = snapshot.currency
            val categorySummary = snapshot.categoryBreakdown.take(5).joinToString("; ") {
                "${it.category.displayName}: ${currency.format(it.amount)} (${it.percentageOfTotal.toInt()}%)"
            }
            val anomalySummary = snapshot.isolationForestReport.flaggedTransactions.take(3).joinToString("; ") {
                "${it.expense.title} (${currency.format(it.expense.amount)} - ${it.reason})"
            }.ifBlank { "None flagged" }
            val clusterSummary = snapshot.kMeansReport.clusters.joinToString("; ") {
                "${it.label}: ${it.transactionCount} txns, avg ${currency.format(it.averageAmount)}"
            }.ifBlank { "Not enough transactions for K-Means yet" }

            val prompt = """
                You are SpendSense AI, a precise personal finance analyst. Analyze ONLY the real metrics below (never invent numbers):
                - Currency: ${currency.code} (${currency.symbol})
                - Monthly Income: ${currency.format(snapshot.totalIncome)}
                - Monthly Budget Target: ${currency.format(snapshot.monthlyBudgetTarget)}
                - Current Month Spent: ${currency.format(snapshot.totalMonthlyExpense)}
                - Last Month Spent: ${currency.format(snapshot.previousMonthExpense)}
                - Projected Month-End Spend: ${currency.format(snapshot.forecastReport.predictedMonthlyTotal)} (Range: ${currency.format(snapshot.forecastReport.confidenceLow)} - ${currency.format(snapshot.forecastReport.confidenceHigh)})
                - Top Categories: $categorySummary
                - Isolation Forest Anomalies: $anomalySummary
                - K-Means Behavioral Clusters: $clusterSummary
                
                Produce a crisp, encouraging, data-grounded financial coaching briefing.
            """.trimIndent()

            val response = withTimeout(TIMEOUT_MS) {
                coachingModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty coaching response.")
            val json = JSONObject(jsonText)
            val stepsJson = json.optJSONArray("actionSteps")
            val steps = if (stepsJson != null) {
                (0 until stepsJson.length()).map { stepsJson.optString(it) }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            Result.success(
                AiCoachingBriefing(
                    executiveSummary = json.optString("executiveSummary"),
                    topHabitObservation = json.optString("topHabitObservation"),
                    actionSteps = steps,
                    riskFlag = json.optString("riskFlag")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI coaching timed out", e)
            Result.failure(Exception("AI coaching timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI coaching failed", e)
            Result.failure(e)
        }
    }

    fun askAdvisorStream(
        question: String,
        snapshot: SpendIntelligenceSnapshot
    ): Flow<String> = flow {
        val currency = snapshot.currency
        val topCats = snapshot.categoryBreakdown.take(5).joinToString(", ") {
            "${it.category.displayName}=${currency.format(it.amount)}"
        }
        val prompt = """
            You are SpendSense AI. Answer the user's personal finance question concisely in 3-5 sentences using ONLY their real metrics below.
            Metrics:
            - Monthly Income: ${currency.format(snapshot.totalIncome)}
            - Monthly Budget Target: ${currency.format(snapshot.monthlyBudgetTarget)}
            - Spent This Month: ${currency.format(snapshot.totalMonthlyExpense)}
            - Remaining Balance: ${currency.format(snapshot.remainingBalance)}
            - Projected Month-End: ${currency.format(snapshot.forecastReport.predictedMonthlyTotal)}
            - Top Categories: $topCats
            - Anomalies Flagged: ${snapshot.isolationForestReport.flaggedTransactions.size}
            
            User Question: $question
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(TIMEOUT_MS) {
            chatModel.generateContentStream(prompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Gemini returned an empty answer.")
        }
    }
}
