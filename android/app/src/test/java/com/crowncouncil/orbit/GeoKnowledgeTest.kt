package com.crowncouncil.orbit

import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.data.GeoBoundaryCatalog
import com.crowncouncil.orbit.data.TouristLocation
import com.crowncouncil.orbit.domain.GeoKnowledge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoKnowledgeTest {
    private val fixture = """
        {
          "type":"FeatureCollection",
          "features":[
            {
              "type":"Feature",
              "properties":{"upazila_id":"1","name":"Test Upazila","district_id":"10","district_name":"Test District","division_id":"2","division_name":"Test Division"},
              "geometry":{"type":"Polygon","coordinates":[
                [[90.0,23.0],[91.0,23.0],[91.0,24.0],[90.0,24.0],[90.0,23.0]],
                [[90.4,23.4],[90.6,23.4],[90.6,23.6],[90.4,23.6],[90.4,23.4]]
              ]}
            },
            {
              "type":"Feature",
              "properties":{"upazila_id":"2","name":"Island Upazila","district_id":"11","district_name":"Island District","division_id":"2","division_name":"Test Division"},
              "geometry":{"type":"MultiPolygon","coordinates":[
                [[[91.2,23.2],[91.3,23.2],[91.3,23.3],[91.2,23.3],[91.2,23.2]]],
                [[[91.4,23.2],[91.5,23.2],[91.5,23.3],[91.4,23.3],[91.4,23.2]]]
              ]}
            }
          ]
        }
    """.trimIndent()

    @Test fun parsesPolygonAndMultiPolygonAndRespectsHoles() {
        val boundaries = GeoBoundaryCatalog.parse(fixture)
        assertTrue(boundaries.size == 2)
        assertTrue(boundaries[0].contains(90.2, 23.2))
        assertFalse(boundaries[0].contains(90.5, 23.5))
        assertFalse(boundaries[0].contains(91.5, 23.5))
        assertTrue(boundaries[1].contains(91.25, 23.25))
        assertTrue(boundaries[1].contains(91.45, 23.25))
        assertTrue(boundaries.all { it.hasAdministrativeMatch })
    }

    @Test fun geographicBrainQueryUsesPolygonDerivedAdministrativeNames() {
        val boundaries = GeoBoundaryCatalog.parse(fixture)
        val tourist = listOf(
            TouristLocation("p1", "River View", "Test group", 23.2, 90.2, "Wrong District", "Wrong Division", "Wrong Upazila", "legacy"),
            TouristLocation("p2", "Island Point", "Test group", 23.25, 91.25, null, null, null, "unmatched")
        )
        val result = GeoKnowledge.answer(
            "show me tourist spots in Test District",
            tourist,
            emptyList<Destination>(),
            boundaries
        )
        assertNotNull(result)
        assertTrue(result!!.contains("River View"))
        assertFalse(result.contains("Wrong District"))
    }

    @Test fun geographicEngineDoesNotInterceptOrdinaryTravelQuestions() {
        assertNull(GeoKnowledge.answer("plan a cheap three day trip", emptyList(), emptyList(), emptyList()))
    }
}
