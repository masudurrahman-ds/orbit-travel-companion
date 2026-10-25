package com.crowncouncil.orbit.domain

import com.crowncouncil.orbit.data.Destination
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Weights for the six scoring factors. Every tier's weights sum to 1. */
data class Weights(val budget: Double, val interest: Double, val time: Double, val weather: Double, val route: Double, val preference: Double) {
    val sum: Double get() = budget + interest + time + weather + route + preference
}

/**
 * BUDGET: cheapest sensible trip. VALUE: affordable but good quality (the proposal's default weights).
 * FANCY: comfort and experience first. Same numbers as the backend planner.
 */
enum class Tier(val label: String, val costFactor: Double, val weights: Weights) {
    BUDGET("Budget", 0.7, Weights(0.34, 0.20, 0.12, 0.14, 0.12, 0.08)),
    VALUE("Value", 1.0, Weights(0.22, 0.20, 0.18, 0.18, 0.12, 0.10)),
    FANCY("Fancy", 1.8, Weights(0.06, 0.28, 0.16, 0.22, 0.08, 0.20))
}

data class PlanQuery(
    val budget: Int = 25_000,
    val travellers: Int = 2,
    val days: Int = 3,
    val month: Int = java.time.LocalDate.now().monthValue,
    val interests: Set<String> = setOf("beach", "food"),
    val tier: Tier = Tier.VALUE
)

data class ScoreBreakdown(
    val total: Double,
    val budget: Double,
    val interest: Double,
    val time: Double,
    val weather: Double,
    val route: Double,
    val preference: Double,
    val estimatedCost: Int,
    val affordable: Boolean
) {
    val percent: Int get() = (total * 100).roundToInt()
}

data class RankedDestination(val destination: Destination, val score: ScoreBreakdown)

fun Destination.tagSet(): Set<String> = tags.split(",").filter { it.isNotBlank() }.toSet()
fun Destination.monthSet(): Set<Int> = bestMonths.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

/** Weighted scoring model: S = sum(w_i * f_i), with every f_i in [0, 1] and the weights summing to 1. */
object ScoringEngine {
    private const val MAX_DISTANCE_KM = 600.0

    fun score(d: Destination, q: PlanQuery, favorite: Boolean = false): ScoreBreakdown {
        val w = q.tier.weights
        val tripCost = d.costPerDayBdt.toDouble() * q.tier.costFactor * q.days * q.travellers
        val budget = if (tripCost <= 0) 1.0 else (q.budget / tripCost).coerceIn(0.0, 1.0)

        val interest = if (q.interests.isEmpty()) 0.5
        else d.tagSet().intersect(q.interests).size.toDouble() / q.interests.size

        val time = 1.0 - min(1.0, abs(q.days - d.recommendedDays).toDouble() / max(q.days, d.recommendedDays))
        val weather = if (q.month in d.monthSet()) 1.0 else 0.4
        val route = 1.0 - min(1.0, d.distanceKm / MAX_DISTANCE_KM)
        val preference = if (favorite) 1.0 else 0.5

        val total = w.budget * budget + w.interest * interest + w.time * time +
            w.weather * weather + w.route * route + w.preference * preference
        return ScoreBreakdown(total, budget, interest, time, weather, route, preference, tripCost.roundToInt(), budget >= 0.8)
    }

    /** Budget and value tours list affordable places first; fancy tours rank on score alone. */
    fun rank(destinations: List<Destination>, q: PlanQuery, favorites: Set<Long> = emptySet()): List<RankedDestination> {
        val rows = destinations.map { RankedDestination(it, score(it, q, it.id in favorites)) }
        return if (q.tier == Tier.FANCY) rows.sortedWith(compareByDescending<RankedDestination> { it.score.total }.thenBy { it.destination.id })
        else rows.sortedWith(compareByDescending<RankedDestination> { it.score.affordable }.thenByDescending { it.score.total }.thenBy { it.destination.id })
    }
}
