package com.crowncouncil.orbit

import com.crowncouncil.orbit.data.Seed
import com.crowncouncil.orbit.data.Visit
import com.crowncouncil.orbit.domain.Badges
import com.crowncouncil.orbit.domain.Brain
import com.crowncouncil.orbit.domain.BrainAction
import com.crowncouncil.orbit.domain.BrainAdvisory
import com.crowncouncil.orbit.domain.BrainContext
import com.crowncouncil.orbit.domain.Lexicon
import com.crowncouncil.orbit.domain.Tier
import com.crowncouncil.orbit.domain.TransportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mirrors the new tests in backend/test (brain, features and research) so the Kotlin port matches the JavaScript reference. */
class FeaturesTest {
    private val brain = Brain(Lexicon(File("src/main/assets/brain-data.json").readText()))
    private val transport = TransportData(File("src/main/assets/travel-costs.json").readText())
    private fun ctx(advisories: List<BrainAdvisory> = emptyList()) = BrainContext(Seed.destinations, Seed.activities, emptyList(), emptyList(), 12, null, advisories)

    @Test fun budgetWordAloneIsAnAmountNotACheapTour() {
        assertNull(brain.extractTier(brain.normalize("my budget is 20k")))
        assertEquals(Tier.BUDGET, brain.extractTier(brain.normalize("budget friendly trip")))
        assertEquals(Tier.VALUE, brain.respond("3 days beach trip budget 20k", ctx()).state.lastQuery!!.tier)
    }

    @Test fun banglaScriptIsUnderstoodOffline() {
        val plan = brain.respond("আমি ৩ দিনের জন্য সমুদ্র সৈকতে যেতে চাই, বাজেট ২০ হাজার", ctx())
        assertTrue(plan.understood)
        assertEquals(3, plan.state.lastQuery!!.days)
        assertEquals(20000, plan.state.lastQuery!!.budget)
        assertEquals(listOf("beach"), plan.state.lastQuery!!.interests)
        assertTrue(brain.respond("কক্সবাজার ভ্রমণে কত খরচ ৪ জন ৫ দিন", ctx()).reply.contains("5 days, 4 travellers"))
        val cheap = brain.respond("শীতে পাহাড়ে ঘুরতে চাই সস্তায়", ctx())
        assertEquals(Tier.BUDGET, cheap.state.lastQuery!!.tier)
        assertEquals(12, cheap.state.lastQuery!!.month)
    }

    @Test fun banglishAndSpellingVariants() {
        assertTrue(brain.respond("cox bazar jete koto khoroch hobe 4 jon", ctx()).reply.contains("Cox's Bazar Beach, 3 days, 4 travellers"))
        assertTrue(brain.respond("saint martin trip", ctx()).reply.contains("Saint Martin's Island"))
        val chattogram = brain.respond("trip to chittagong", ctx()).plan!!.destinationId
        assertEquals("Chattogram", Seed.destinations.first { it.id == chattogram }.district)
    }

    @Test fun aCasualForgetItNeverWipesMemory() {
        assertTrue(brain.respond("ভুলে যাও", ctx()).actions.isEmpty())
        assertEquals(BrainAction.ForgetAll, brain.respond("সব ভুলে যাও", ctx()).actions.first())
    }

    @Test fun advisoriesAreAddedForTheirPlacesOnly() {
        val adv = listOf(
            BrainAdvisory("national", null, "info", "Eid travel rush", "Expect crowded buses."),
            BrainAdvisory("district", "Rangamati", "danger", "Landslide risk", "Avoid hill roads after heavy rain.")
        )
        assertTrue(brain.respond("best time to visit Sajek", ctx(adv)).reply.contains("Advisory (danger): Landslide risk. Avoid hill roads after heavy rain."))
        assertTrue(brain.respond("tell me about Jaflong", ctx(adv)).reply.contains("Advisory (info): Eid travel rush"))
        assertFalse(brain.respond("tell me about Jaflong", ctx()).reply.contains("Advisory"))
    }

    @Test fun saintMartinBestMonthsFollowTheAccessRules() {
        val sm = Seed.destinations.first { it.name == "Saint Martin's Island" }
        assertEquals("11,12,1", sm.bestMonths)
    }

    @Test fun badgesEarnProgressAndCountEachSpotOnce() {
        val sylhet = Seed.destinations.filter { it.district == "Sylhet" }
        val none = Badges.compute(Seed.destinations, emptyList())
        assertTrue(none.all { !it.earned && it.progress == 0 })
        val visits = listOf(Visit(sylhet[0].id, 0L), Visit(sylhet[0].id, 0L), Visit(sylhet[1].id, 0L, null, "lovely", "gps"))
        val by = Badges.compute(Seed.destinations, visits).associateBy { it.id }
        assertTrue(by.getValue("first_step").earned)
        assertEquals(2, by.getValue("explorer_10").progress)
        assertEquals(1, by.getValue("storyteller").progress)
        assertEquals(1, by.getValue("verified_5").progress)
        val all = Badges.compute(Seed.destinations, Seed.destinations.map { Visit(it.id, 0L, null, "x", "gps") })
        assertTrue(all.all { it.earned })
    }

    @Test fun gettingThereUsesSourcedFaresAndLabelsEstimates() {
        val cox = transport.describe(Seed.destinations.first { it.name == "Cox's Bazar Beach" }).lines.joinToString("\n")
        assertTrue(cox.contains("Tk 900"))
        assertTrue(cox.contains("Tk 2,656"))
        val jaflong = transport.describe(Seed.destinations.first { it.name == "Jaflong" })
        assertTrue(jaflong.lines.first().contains("estimate"))
        assertTrue(jaflong.footnote.contains("confidence low"))
        assertTrue(transport.describe(Seed.destinations.first { it.district == "Dhaka" }).lines.first().contains("Within Dhaka"))
        assertTrue(transport.checkedOn.isNotEmpty())
    }
}
