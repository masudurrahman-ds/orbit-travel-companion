package com.crowncouncil.orbit

import com.crowncouncil.orbit.data.ActivityOption
import com.crowncouncil.orbit.data.Seed
import com.crowncouncil.orbit.domain.ItineraryGenerator
import com.crowncouncil.orbit.domain.PlanQuery
import com.crowncouncil.orbit.domain.ScoringEngine
import com.crowncouncil.orbit.domain.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {
    private val query = PlanQuery(budget = 30_000, travellers = 2, days = 3, month = 12, interests = setOf("beach", "food"))

    @Test fun weightsSumToOneForEveryTier() {
        Tier.entries.forEach { assertEquals(it.name, 1.0, it.weights.sum, 1e-9) }
    }

    @Test fun budgetTierCostsLessAndFancyMoreThanValue() {
        val cox = Seed.destinations.first { it.name == "Cox's Bazar Beach" }
        val cost = { t: Tier -> ScoringEngine.score(cox, query.copy(tier = t)).estimatedCost }
        assertTrue(cost(Tier.BUDGET) < cost(Tier.VALUE) && cost(Tier.VALUE) < cost(Tier.FANCY))
    }

    @Test fun tightBudgetListsAffordableSpotsFirst() {
        val rows = ScoringEngine.rank(Seed.destinations, query.copy(budget = 12_000, tier = Tier.BUDGET))
        val firstOver = rows.indexOfFirst { !it.score.affordable }
        assertTrue(rows.drop(firstOver).none { it.score.affordable })
    }

    @Test fun cheapestActivitiesForBudgetAndPriciestForFancy() {
        val coxId = Seed.destinations.first { it.name == "Cox's Bazar Beach" }.id
        val acts = Seed.activities.filter { it.destinationId == coxId }
        val cost = { t: Tier -> ItineraryGenerator.generate(1, 2, acts, emptySet(), tier = t).sumOf { i -> acts.first { a -> a.id == i.activityId }.estimatedCost } }
        assertTrue(cost(Tier.BUDGET) <= cost(Tier.FANCY))
    }

    @Test fun scoresStayInUnitRange() {
        Seed.destinations.forEach {
            val s = ScoringEngine.score(it, query).total
            assertTrue("${it.name} scored $s", s in 0.0..1.0)
        }
    }

    @Test fun beachAndFoodInterestRanksCoxsBazarFirst() {
        val top = ScoringEngine.rank(Seed.destinations, query).first().destination
        assertEquals("Cox's Bazar", top.district)
    }

    @Test fun tinyBudgetLowersBudgetFit() {
        val cox = Seed.destinations.first { it.name == "Cox's Bazar Beach" }
        val rich = ScoringEngine.score(cox, query.copy(budget = 100_000)).budget
        val poor = ScoringEngine.score(cox, query.copy(budget = 5_000)).budget
        assertTrue(poor < rich)
    }

    @Test fun coversAll64Districts() {
        assertEquals(64, Seed.destinations.map { it.district }.toSet().size)
    }

    @Test fun everyDestinationHasTwoIndoorActivitiesForRainReplanning() {
        Seed.destinations.forEach { d ->
            val indoor = Seed.activities.count { it.destinationId == d.id && it.isIndoor }
            assertTrue("${d.name} has $indoor indoor", indoor >= 2)
        }
    }

    @Test fun favoriteRaisesScore() {
        val cox = Seed.destinations.first()
        assertTrue(ScoringEngine.score(cox, query, favorite = true).total > ScoringEngine.score(cox, query).total)
    }

    @Test fun rainyDayGetsOnlyIndoorActivities() {
        val coxId = Seed.destinations.first { it.name == "Cox's Bazar Beach" }.id
        val acts: List<ActivityOption> = Seed.activities.filter { it.destinationId == coxId }
        val plan = ItineraryGenerator.generate(1, 3, acts, setOf("beach"), rainyDays = setOf(1))
        val byId = acts.associateBy { it.id }
        val day1 = plan.filter { it.dayNo == 1 }.map { byId.getValue(it.activityId) }
        assertTrue(day1.all { it.isIndoor })
    }
}
