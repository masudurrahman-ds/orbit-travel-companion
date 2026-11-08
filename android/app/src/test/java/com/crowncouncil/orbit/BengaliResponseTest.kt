package com.crowncouncil.orbit

import com.crowncouncil.orbit.domain.BengaliResponse
import org.junit.Assert.assertTrue
import org.junit.Test

class BengaliResponseTest {
    @Test fun localisesCommonTravelGuidanceAndPreservesPlaceNamesAndNumbers() {
        val result = BengaliResponse.render(
            "Best months for Cox's Bazar: November, December. About BDT 12,500 for 2 people, 3 days. Costs are estimates."
        )
        assertTrue(result.contains("Cox's Bazar"))
        assertTrue(result.contains("12,500"))
        assertTrue(result.contains("২") || result.contains("2"))
        assertTrue(result.contains("ভ্রমণের জন্য ভালো সময়"))
        assertTrue(result.contains("আনুমানিক হিসাব"))
    }

    @Test fun localisesWarmConversationAndSafetyCaveat() {
        val result = BengaliResponse.render(
            "You are welcome. These are straight-line distances, not driving distances or travel times."
        )
        assertTrue(result.contains("স্বাগতম"))
        assertTrue(result.contains("সড়কপথের দূরত্ব"))
    }
}
