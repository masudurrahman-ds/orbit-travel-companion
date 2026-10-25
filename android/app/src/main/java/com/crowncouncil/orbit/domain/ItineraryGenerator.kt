package com.crowncouncil.orbit.domain

import com.crowncouncil.orbit.data.ActivityOption
import com.crowncouncil.orbit.data.ItineraryItem

object ItineraryGenerator {
    val SLOTS = listOf(9, 13, 16)

    /**
     * Fills three slots per day, interest matches first. Within that, BUDGET picks the cheapest activities,
     * FANCY the priciest, VALUE the most hours of experience per taka. On rainy days only indoor
     * activities are scheduled, so re-planning moves outdoor items to dry days.
     */
    fun generate(
        tripId: Long,
        days: Int,
        activities: List<ActivityOption>,
        interests: Set<String>,
        rainyDays: Set<Int> = emptySet(),
        tier: Tier = Tier.VALUE
    ): List<ItineraryItem> {
        val tierKey: (ActivityOption) -> Double = when (tier) {
            Tier.BUDGET -> { a -> a.estimatedCost.toDouble() }
            Tier.FANCY -> { a -> -a.estimatedCost.toDouble() }
            Tier.VALUE -> { a -> -(a.durationHours.toDouble() / (a.estimatedCost + 100)) }
        }
        val ranked = activities.sortedWith(
            compareByDescending<ActivityOption> { if (it.category in interests) 1 else 0 }
                .thenBy(tierKey).thenBy { it.id }
        )
        val used = mutableSetOf<Long>()
        val result = mutableListOf<ItineraryItem>()
        for (day in 1..days) {
            for (hour in SLOTS) {
                val pick = ranked.firstOrNull { it.id !in used && (day !in rainyDays || it.isIndoor) } ?: continue
                used += pick.id
                result += ItineraryItem(tripId = tripId, activityId = pick.id, dayNo = day, startHour = hour)
            }
        }
        return result
    }
}
