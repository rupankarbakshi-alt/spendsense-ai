package com.example.ml

import com.example.data.model.Expense
import java.util.Calendar
import kotlin.math.ln
import kotlin.math.sqrt

data class SpendingCluster(
    val clusterId: Int,
    val label: String,
    val description: String,
    val transactionCount: Int,
    val totalAmount: Double,
    val averageAmount: Double,
    val sharePercentage: Double,
    val dominantCategory: String
)

data class KMeansReport(
    val modelTrained: Boolean,
    val sampleCount: Int,
    val minimumRequiredSamples: Int = 6,
    val clusters: List<SpendingCluster>,
    val inertia: Double,
    val limitationMessage: String?
)

/**
 * Pure Kotlin K-Means (k=3) clustering over standardized expense feature vectors:
 * [log1p(amount), dayOfMonth/31, isWeekend, hourOfDay/24]
 */
object KMeansClusterer {

    private const val MIN_SAMPLES = 6
    private const val MAX_ITERATIONS = 25

    fun cluster(expenses: List<Expense>): KMeansReport {
        if (expenses.size < MIN_SAMPLES) {
            return KMeansReport(
                modelTrained = false,
                sampleCount = expenses.size,
                clusters = emptyList(),
                inertia = 0.0,
                limitationMessage = "K-Means clustering requires at least $MIN_SAMPLES transactions to group spending behaviors (currently ${expenses.size})."
            )
        }

        val rawVectors = expenses.map { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.dateEpochMillis }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) 1.0 else 0.0
            doubleArrayOf(
                ln(1.0 + exp.amount),
                cal.get(Calendar.DAY_OF_MONTH) / 31.0,
                isWeekend,
                cal.get(Calendar.HOUR_OF_DAY) / 24.0
            )
        }

        val dim = rawVectors.first().size
        val means = DoubleArray(dim) { d -> rawVectors.map { it[d] }.average() }
        val stds = DoubleArray(dim) { d ->
            val m = means[d]
            val variance = rawVectors.map { (it[d] - m) * (it[d] - m) }.average()
            sqrt(variance).coerceAtLeast(1e-5)
        }

        val standardized = rawVectors.map { vec ->
            DoubleArray(dim) { d -> (vec[d] - means[d]) / stds[d] }
        }

        val k = 3.coerceAtMost(expenses.distinctBy { it.amount }.size.coerceAtLeast(1))
        if (k < 2) {
            val total = expenses.sumOf { it.amount }
            return KMeansReport(
                modelTrained = true,
                sampleCount = expenses.size,
                clusters = listOf(
                    SpendingCluster(
                        clusterId = 0,
                        label = "Uniform Spending",
                        description = "All transactions currently fall into a single uniform price tier.",
                        transactionCount = expenses.size,
                        totalAmount = total,
                        averageAmount = total / expenses.size,
                        sharePercentage = 100.0,
                        dominantCategory = expenses.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: "Other"
                    )
                ),
                inertia = 0.0,
                limitationMessage = null
            )
        }

        // Deterministic K-Means++ style farthest-point initialization sorted by amount
        val sortedIndices = expenses.indices.sortedBy { expenses[it].amount }
        val centroids = Array(k) { clusterIdx ->
            val pickIdx = (clusterIdx * (sortedIndices.size - 1)) / (k - 1).coerceAtLeast(1)
            standardized[sortedIndices[pickIdx]].clone()
        }

        val assignments = IntArray(expenses.size) { 0 }

        repeat(MAX_ITERATIONS) {
            var changed = false
            for (i in standardized.indices) {
                val vec = standardized[i]
                var bestCluster = 0
                var bestDist = Double.POSITIVE_INFINITY
                for (c in 0 until k) {
                    val dist = squaredDistance(vec, centroids[c])
                    if (dist < bestDist) {
                        bestDist = dist
                        bestCluster = c
                    }
                }
                if (assignments[i] != bestCluster) {
                    assignments[i] = bestCluster
                    changed = true
                }
            }

            if (!changed) return@repeat

            for (c in 0 until k) {
                val members = standardized.indices.filter { assignments[it] == c }
                if (members.isNotEmpty()) {
                    for (d in 0 until dim) {
                        centroids[c][d] = members.map { standardized[it][d] }.average()
                    }
                }
            }
        }

        var inertia = 0.0
        for (i in standardized.indices) {
            inertia += squaredDistance(standardized[i], centroids[assignments[i]])
        }

        val totalSpend = expenses.sumOf { it.amount }.coerceAtLeast(1.0)
        val rawClusters = (0 until k).mapNotNull { c ->
            val members = expenses.indices.filter { assignments[it] == c }.map { expenses[it] }
            if (members.isEmpty()) return@mapNotNull null
            val clusterTotal = members.sumOf { it.amount }
            val avg = clusterTotal / members.size
            val dominantCat = members.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: "Other"
            Triple(members, clusterTotal, Pair(avg, dominantCat))
        }.sortedBy { it.third.first }

        val labels = listOf(
            "Routine Essentials" to "Frequent, smaller everyday outlays and regular micro-purchases.",
            "Lifestyle & Recurring" to "Mid-ticket discretionary dining, shopping, and routine bills.",
            "High-Impact Outlays" to "Large ticket purchases, major bills, or travel spikes."
        )

        val clusters = rawClusters.mapIndexed { idx, (members, clusterTotal, stats) ->
            val (label, desc) = labels.getOrElse(idx) { "Segment ${idx + 1}" to "Grouped spending cluster." }
            SpendingCluster(
                clusterId = idx,
                label = label,
                description = desc,
                transactionCount = members.size,
                totalAmount = clusterTotal,
                averageAmount = stats.first,
                sharePercentage = (clusterTotal / totalSpend) * 100.0,
                dominantCategory = stats.second
            )
        }

        return KMeansReport(
            modelTrained = true,
            sampleCount = expenses.size,
            clusters = clusters,
            inertia = inertia,
            limitationMessage = null
        )
    }

    private fun squaredDistance(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum
    }
}
