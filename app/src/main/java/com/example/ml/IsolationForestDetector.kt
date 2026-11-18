package com.example.ml

import com.example.data.model.Expense
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

data class AnomalyDetectionResult(
    val expense: Expense,
    val anomalyScore: Double,
    val reason: String,
    val severity: AnomalySeverity
)

enum class AnomalySeverity(val label: String) {
    HIGH("High Anomaly"),
    MEDIUM("Unusual Spike")
}

data class IsolationForestReport(
    val modelTrained: Boolean,
    val sampleCount: Int,
    val minimumRequiredSamples: Int = 8,
    val flaggedTransactions: List<AnomalyDetectionResult>,
    val categorySpikes: List<String>,
    val limitationMessage: String?
)

/**
 * Pure Kotlin implementation of Isolation Forest (Liu, Ting & Zhou, 2008).
 * Partitions expense feature vectors [amount, log1p(amount), dayOfMonth, hourOfDay, categoryRelativeRatio]
 * across an ensemble of randomized Isolation Trees and scores anomalies by shorter expected path length.
 */
object IsolationForestDetector {

    private sealed interface ITree {
        data class Node(
            val splitFeature: Int,
            val splitValue: Double,
            val left: ITree,
            val right: ITree
        ) : ITree

        data class Leaf(val size: Int) : ITree
    }

    private const val NUM_TREES = 64
    private const val MIN_SAMPLES = 8
    private const val ANOMALY_SCORE_THRESHOLD = 0.62

    fun detect(expenses: List<Expense>): IsolationForestReport {
        if (expenses.isEmpty()) {
            return IsolationForestReport(
                modelTrained = false,
                sampleCount = 0,
                flaggedTransactions = emptyList(),
                categorySpikes = emptyList(),
                limitationMessage = "No transactions recorded yet. Add expenses to activate Isolation Forest anomaly detection."
            )
        }

        val categoryMedians = expenses.groupBy { it.category }.mapValues { (_, list) ->
            median(list.map { it.amount })
        }

        val categorySpikes = detectCategoryMonthlySpikes(expenses)

        if (expenses.size < MIN_SAMPLES) {
            // Transparently report insufficient history and apply clear preliminary threshold check
            val globalMedian = median(expenses.map { it.amount }).coerceAtLeast(1.0)
            val preliminary = expenses.mapNotNull { exp ->
                val ratio = exp.amount / globalMedian
                if (expenses.size >= 3 && ratio >= 2.8) {
                    AnomalyDetectionResult(
                        expense = exp,
                        anomalyScore = (0.58 + (ratio - 2.8) * 0.05).coerceAtMost(0.88),
                        reason = "Preliminary check: ${"%.1f".format(ratio)}x above your current median spend",
                        severity = AnomalySeverity.MEDIUM
                    )
                } else null
            }.sortedByDescending { it.anomalyScore }

            return IsolationForestReport(
                modelTrained = false,
                sampleCount = expenses.size,
                flaggedTransactions = preliminary,
                categorySpikes = categorySpikes,
                limitationMessage = "Isolation Forest requires at least $MIN_SAMPLES transactions to partition spending behavior (currently ${expenses.size}). Showing preliminary ratio checks."
            )
        }

        val features = expenses.map { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.dateEpochMillis }
            val catMed = (categoryMedians[exp.category] ?: exp.amount).coerceAtLeast(1.0)
            doubleArrayOf(
                exp.amount,
                ln(1.0 + exp.amount),
                cal.get(Calendar.DAY_OF_MONTH).toDouble(),
                cal.get(Calendar.HOUR_OF_DAY).toDouble(),
                exp.amount / catMed
            )
        }

        val sampleSize = features.size.coerceAtMost(256)
        val maxDepth = ceil(ln(sampleSize.toDouble()) / ln(2.0)).toInt().coerceAtLeast(2)
        val rng = Random(42L + expenses.size)

        val forest = List(NUM_TREES) {
            val subSample = if (features.size > sampleSize) {
                features.shuffled(rng).take(sampleSize)
            } else {
                features
            }
            buildTree(subSample, currentDepth = 0, maxDepth = maxDepth, rng = rng)
        }

        val cNorm = cFactor(sampleSize)
        val globalMean = expenses.map { it.amount }.average().coerceAtLeast(1.0)
        val globalMedian = median(expenses.map { it.amount }).coerceAtLeast(1.0)

        val flagged = expenses.indices.mapNotNull { idx ->
            val vec = features[idx]
            val exp = expenses[idx]
            val avgPathLength = forest.map { tree -> pathLength(vec, tree, 0) }.average()
            val score = 2.0.pow(-avgPathLength / cNorm)

            val catMed = (categoryMedians[exp.category] ?: globalMedian).coerceAtLeast(1.0)
            val catRatio = exp.amount / catMed
            val globalRatio = exp.amount / globalMedian

            // Only surface high-scoring anomalies that represent genuine upward or unusual deviations
            if (score >= ANOMALY_SCORE_THRESHOLD && (exp.amount >= globalMean * 1.4 || catRatio >= 2.0)) {
                val reason = buildString {
                    if (catRatio >= 2.2) {
                        append("${"%.1f".format(catRatio)}x higher than your usual ${exp.category} spend")
                    } else if (globalRatio >= 2.2) {
                        append("${"%.1f".format(globalRatio)}x above your typical transaction median")
                    } else {
                        append("Isolated by multi-feature spending pattern deviation")
                    }
                }
                AnomalyDetectionResult(
                    expense = exp,
                    anomalyScore = score.coerceIn(0.0, 0.99),
                    reason = reason,
                    severity = if (score >= 0.72) AnomalySeverity.HIGH else AnomalySeverity.MEDIUM
                )
            } else null
        }.sortedByDescending { it.anomalyScore }

        return IsolationForestReport(
            modelTrained = true,
            sampleCount = expenses.size,
            flaggedTransactions = flagged,
            categorySpikes = categorySpikes,
            limitationMessage = null
        )
    }

    private fun buildTree(
        data: List<DoubleArray>,
        currentDepth: Int,
        maxDepth: Int,
        rng: Random
    ): ITree {
        if (currentDepth >= maxDepth || data.size <= 1) {
            return ITree.Leaf(data.size)
        }

        val numFeatures = data.first().size
        val candidateFeatures = (0 until numFeatures).shuffled(rng)
        for (featureIdx in candidateFeatures) {
            var minVal = Double.POSITIVE_INFINITY
            var maxVal = Double.NEGATIVE_INFINITY
            for (row in data) {
                val v = row[featureIdx]
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
            if (maxVal > minVal) {
                val splitVal = minVal + rng.nextDouble() * (maxVal - minVal)
                val leftData = ArrayList<DoubleArray>()
                val rightData = ArrayList<DoubleArray>()
                for (row in data) {
                    if (row[featureIdx] < splitVal) leftData.add(row) else rightData.add(row)
                }
                if (leftData.isNotEmpty() && rightData.isNotEmpty()) {
                    return ITree.Node(
                        splitFeature = featureIdx,
                        splitValue = splitVal,
                        left = buildTree(leftData, currentDepth + 1, maxDepth, rng),
                        right = buildTree(rightData, currentDepth + 1, maxDepth, rng)
                    )
                }
            }
        }
        return ITree.Leaf(data.size)
    }

    private fun pathLength(x: DoubleArray, tree: ITree, depth: Int): Double {
        return when (tree) {
            is ITree.Leaf -> depth + cFactor(tree.size)
            is ITree.Node -> {
                if (x[tree.splitFeature] < tree.splitValue) {
                    pathLength(x, tree.left, depth + 1)
                } else {
                    pathLength(x, tree.right, depth + 1)
                }
            }
        }
    }

    private fun cFactor(n: Int): Double {
        if (n <= 1) return 0.0
        if (n == 2) return 1.0
        val h = ln((n - 1).toDouble()) + 0.5772156649
        return 2.0 * h - (2.0 * (n - 1).toDouble()) / n.toDouble()
    }

    private fun detectCategoryMonthlySpikes(expenses: List<Expense>): List<String> {
        val nowCal = Calendar.getInstance()
        val currentMonthKey = nowCal.get(Calendar.YEAR) * 100 + nowCal.get(Calendar.MONTH)

        val byCategoryMonth = expenses.groupBy { it.category }.mapValues { (_, list) ->
            list.groupBy { exp ->
                val c = Calendar.getInstance().apply { timeInMillis = exp.dateEpochMillis }
                c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
            }.mapValues { (_, monthItems) -> monthItems.sumOf { it.amount } }
        }

        val alerts = mutableListOf<String>()
        for ((category, monthTotals) in byCategoryMonth) {
            val currentSpend = monthTotals[currentMonthKey] ?: 0.0
            val priorMonths = monthTotals.filterKeys { it < currentMonthKey }.values
            if (priorMonths.isNotEmpty() && currentSpend > 0.0) {
                val priorAvg = priorMonths.average().coerceAtLeast(1.0)
                val pctIncrease = ((currentSpend - priorAvg) / priorAvg) * 100.0
                if (pctIncrease >= 30.0) {
                    alerts.add(
                        "Your $category expenses are ${pctIncrease.toInt()}% higher than your usual monthly spending."
                    )
                }
            }
        }
        return alerts
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }
}
