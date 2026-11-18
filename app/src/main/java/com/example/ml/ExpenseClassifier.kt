package com.example.ml

import com.example.data.model.CategoryRule
import com.example.data.model.ExpenseCategory

data class ClassificationResult(
    val category: ExpenseCategory,
    val confidence: Double,
    val matchedSource: String
)

/**
 * Multi-class intelligent expense classifier combining:
 * 1. User-learned adaptive override rules (highest priority)
 * 2. Weighted token + bigram keyword scoring across all 9 SpendSense categories
 * 3. Amount-prior heuristics when text is sparse
 */
object ExpenseClassifier {

    private val vocabulary: Map<ExpenseCategory, Map<String, Double>> = mapOf(
        ExpenseCategory.FOOD_DINING to mapOf(
            "zomato" to 3.5, "swiggy" to 3.5, "uber eats" to 3.5, "doordash" to 3.5,
            "starbucks" to 3.2, "blue tokai" to 3.2, "cafe" to 2.8, "coffee" to 2.8,
            "restaurant" to 3.0, "dining" to 3.0, "dinner" to 2.6, "lunch" to 2.6,
            "breakfast" to 2.6, "pizza" to 2.9, "burger" to 2.8, "biryani" to 2.9,
            "bakery" to 2.6, "grocery" to 2.7, "groceries" to 2.7, "blinkit" to 3.0,
            "zepto" to 3.0, "instamart" to 3.0, "bigbasket" to 3.0, "supermarket" to 2.7,
            "dominos" to 3.2, "mcdonalds" to 3.2, "kfc" to 3.2, "tea" to 2.0,
            "chai" to 2.4, "snack" to 2.2, "food" to 2.8, "meal" to 2.5, "haldiram" to 3.0
        ),
        ExpenseCategory.SHOPPING to mapOf(
            "amazon" to 3.3, "flipkart" to 3.3, "myntra" to 3.4, "ajio" to 3.4,
            "zara" to 3.3, "uniqlo" to 3.3, "h&m" to 3.3, "nike" to 3.2,
            "adidas" to 3.2, "nykaa" to 3.3, "apple store" to 3.2, "croma" to 3.1,
            "electronics" to 2.8, "clothing" to 2.9, "shoes" to 2.8, "sneakers" to 3.0,
            "mall" to 2.5, "shopping" to 3.2, "apparel" to 2.8, "watch" to 2.4,
            "furniture" to 2.7, "ikea" to 3.2, "decathlon" to 3.0, "gadget" to 2.8,
            "accessories" to 2.5, "gift" to 2.3, "order" to 1.4
        ),
        ExpenseCategory.TRANSPORTATION to mapOf(
            "uber" to 3.4, "ola" to 3.4, "rapido" to 3.4, "lyft" to 3.4,
            "metro" to 3.2, "subway" to 3.0, "bus" to 2.7, "train" to 2.7,
            "cab" to 3.1, "taxi" to 3.1, "auto" to 2.5, "rickshaw" to 3.0,
            "petrol" to 3.4, "diesel" to 3.4, "fuel" to 3.3, "shell" to 3.0,
            "hpcl" to 3.2, "indian oil" to 3.2, "parking" to 2.9, "toll" to 3.0,
            "fastag" to 3.3, "commute" to 2.8, "transport" to 3.0, "ev charge" to 3.0
        ),
        ExpenseCategory.EDUCATION to mapOf(
            "coursera" to 3.5, "udemy" to 3.5, "edx" to 3.5, "tuition" to 3.5,
            "university" to 3.2, "college" to 3.2, "school" to 3.1, "course" to 2.9,
            "book" to 2.7, "books" to 2.8, "textbook" to 3.2, "kindle" to 2.6,
            "exam" to 3.0, "certification" to 3.1, "workshop" to 2.7, "seminar" to 2.7,
            "stationery" to 2.8, "library" to 2.8, "bootcamp" to 3.2, "learning" to 2.5,
            "notebook" to 2.3
        ),
        ExpenseCategory.ENTERTAINMENT to mapOf(
            "netflix" to 3.5, "spotify" to 3.5, "prime video" to 3.5, "hotstar" to 3.5,
            "youtube" to 3.1, "pvr" to 3.4, "inox" to 3.4, "bookmyshow" to 3.5,
            "movie" to 3.2, "cinema" to 3.2, "concert" to 3.3, "gaming" to 3.2,
            "steam" to 3.3, "playstation" to 3.4, "xbox" to 3.4, "nintendo" to 3.4,
            "club" to 2.4, "bowling" to 3.0, "arcade" to 3.0, "ticket" to 2.1,
            "music" to 2.5, "subscription" to 1.8
        ),
        ExpenseCategory.HEALTHCARE to mapOf(
            "apollo" to 3.4, "pharmeasy" to 3.5, "1mg" to 3.5, "netmeds" to 3.5,
            "pharmacy" to 3.4, "chemist" to 3.3, "medicine" to 3.4, "medical" to 3.2,
            "hospital" to 3.5, "clinic" to 3.4, "doctor" to 3.4, "dentist" to 3.4,
            "therapy" to 3.1, "lab" to 2.8, "diagnostic" to 3.2, "blood test" to 3.4,
            "health" to 2.6, "checkup" to 3.2, "optical" to 2.8, "gym" to 2.3,
            "fitness" to 2.2
        ),
        ExpenseCategory.BILLS_UTILITIES to mapOf(
            "electricity" to 3.5, "bescom" to 3.5, "water bill" to 3.5, "gas bill" to 3.4,
            "internet" to 3.2, "wifi" to 3.3, "broadband" to 3.4, "airtel" to 3.3,
            "jio" to 3.3, "vodafone" to 3.2, "recharge" to 3.1, "mobile bill" to 3.3,
            "rent" to 3.5, "maintenance" to 2.9, "utility" to 3.3, "utilities" to 3.3,
            "insurance" to 3.1, "emi" to 3.2, "bill" to 2.4, "postpaid" to 3.2,
            "cloud" to 2.2, "aws" to 2.6
        ),
        ExpenseCategory.TRAVEL to mapOf(
            "indigo" to 3.5, "air india" to 3.5, "vistara" to 3.5, "flight" to 3.4,
            "airline" to 3.4, "makemytrip" to 3.5, "goibibo" to 3.5, "airbnb" to 3.5,
            "booking.com" to 3.5, "hotel" to 3.3, "resort" to 3.3, "hostel" to 3.0,
            "irctc" to 3.4, "railway" to 3.1, "luggage" to 2.9, "visa" to 2.7,
            "airport" to 3.2, "trip" to 2.6, "vacation" to 3.0, "tour" to 2.7
        )
    )

    fun classify(
        title: String,
        merchant: String = "",
        notes: String = "",
        amount: Double? = null,
        userRules: List<CategoryRule> = emptyList()
    ): ClassificationResult {
        val combined = "$title $merchant $notes".trim().lowercase()
        if (combined.isBlank()) {
            return ClassificationResult(ExpenseCategory.OTHER, 0.35, "Default fallback")
        }

        // 1. Check user-learned rules first (exact or substring keyword match)
        val matchedRule = userRules
            .sortedByDescending { it.keyword.length }
            .firstOrNull { rule ->
                val kw = rule.keyword.trim().lowercase()
                kw.isNotBlank() && combined.contains(kw)
            }
        if (matchedRule != null) {
            return ClassificationResult(
                category = ExpenseCategory.fromDisplayName(matchedRule.category),
                confidence = 0.96,
                matchedSource = "Learned from your past edit ('${matchedRule.keyword}')"
            )
        }

        // 2. Multi-class weighted keyword & bigram scorer
        val tokens = combined.split(Regex("[^a-z0-9.+&]+")).filter { it.isNotBlank() }
        val scores = mutableMapOf<ExpenseCategory, Double>()
        val bestHits = mutableMapOf<ExpenseCategory, String>()

        for ((category, keywords) in vocabulary) {
            var score = 0.0
            var topToken = ""
            var topWeight = 0.0
            for ((keyword, weight) in keywords) {
                val matched = if (keyword.contains(" ")) {
                    combined.contains(keyword)
                } else {
                    tokens.any { it == keyword || (keyword.length >= 4 && it.contains(keyword)) }
                }
                if (matched) {
                    score += weight
                    if (weight > topWeight) {
                        topWeight = weight
                        topToken = keyword
                    }
                }
            }
            if (score > 0.0) {
                scores[category] = score
                bestHits[category] = topToken
            }
        }

        // Light amount priors for disambiguation
        if (amount != null && amount > 0.0) {
            if (amount >= 8000.0 && scores.containsKey(ExpenseCategory.TRAVEL)) {
                scores[ExpenseCategory.TRAVEL] = (scores[ExpenseCategory.TRAVEL] ?: 0.0) + 0.5
            }
            if (amount in 20.0..400.0 && scores.containsKey(ExpenseCategory.TRANSPORTATION)) {
                scores[ExpenseCategory.TRANSPORTATION] = (scores[ExpenseCategory.TRANSPORTATION] ?: 0.0) + 0.3
            }
        }

        val winner = scores.maxByOrNull { it.value }
        if (winner == null || winner.value < 1.5) {
            return ClassificationResult(
                category = ExpenseCategory.OTHER,
                confidence = 0.42,
                matchedSource = "Low keyword signal — default category"
            )
        }

        val totalScore = scores.values.sum().coerceAtLeast(1.0)
        val rawConfidence = (winner.value / totalScore) * 0.55 + (winner.value.coerceAtMost(6.0) / 6.0) * 0.40
        val confidence = rawConfidence.coerceIn(0.55, 0.95)
        val hit = bestHits[winner.key].orEmpty()

        return ClassificationResult(
            category = winner.key,
            confidence = confidence,
            matchedSource = if (hit.isNotBlank()) "ML keyword classifier ('$hit')" else "ML classifier"
        )
    }
}
