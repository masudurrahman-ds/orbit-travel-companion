package com.crowncouncil.orbit.domain

import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.data.Visit

data class Badge(val id: String, val title: String, val description: String, val progress: Int, val target: Int) {
    val earned: Boolean get() = progress >= target
}

/** Achievement badges, computed from a traveller's visits. Mirrors backend/src/badges.js. */
object Badges {
    private class Counts(val spots: Int, val districts: Int, val divisions: Int, val tags: Map<String, Int>, val verified: Int, val notes: Int)

    fun compute(destinations: List<Destination>, visits: List<Visit>): List<Badge> {
        val byId = destinations.associateBy { it.id }
        val seen = LinkedHashMap<Long, Visit>()
        for (v in visits) if (v.destinationId in byId) seen[v.destinationId] = v      // one row per spot
        val districts = mutableSetOf<String>(); val divisions = mutableSetOf<String>()
        val tags = mutableMapOf<String, Int>(); var verified = 0; var notes = 0
        for ((id, v) in seen) {
            val d = byId.getValue(id)
            districts += d.district; if (d.division.isNotEmpty()) divisions += d.division
            d.tagSet().forEach { tags[it] = (tags[it] ?: 0) + 1 }
            if (v.source == "gps") verified++
            if (v.note.isNotBlank()) notes++
        }
        val c = Counts(seen.size, districts.size, divisions.size, tags, verified, notes)
        fun b(id: String, title: String, desc: String, target: Int, count: Int) = Badge(id, title, desc, minOf(count, target), target)
        return listOf(
            b("first_step", "First Step", "Visit your first spot", 1, c.spots),
            b("explorer_10", "Explorer", "Visit 10 spots", 10, c.spots),
            b("district_5", "District Hopper", "Visit 5 districts", 5, c.districts),
            b("district_16", "Quarter Way", "Visit 16 of 64 districts", 16, c.districts),
            b("half_way", "Half Way", "Visit 32 of 64 districts", 32, c.districts),
            b("all_divisions", "All 8 Divisions", "Visit a spot in every division", 8, c.divisions),
            b("beach_lover", "Beach Lover", "Visit 5 beach spots", 5, c.tags["beach"] ?: 0),
            b("hill_explorer", "Hill Explorer", "Visit 5 hill spots", 5, c.tags["hills"] ?: 0),
            b("heritage_hunter", "Heritage Hunter", "Visit 8 culture spots", 8, c.tags["culture"] ?: 0),
            b("nature_walker", "Nature Walker", "Visit 8 nature spots", 8, c.tags["nature"] ?: 0),
            b("foodie_trail", "Foodie Trail", "Visit 5 food spots", 5, c.tags["food"] ?: 0),
            b("adventurer", "Adventurer", "Visit 4 adventure spots", 4, c.tags["adventure"] ?: 0),
            b("verified_5", "On the Ground", "5 GPS-verified check-ins", 5, c.verified),
            b("storyteller", "Storyteller", "Write notes on 3 visits", 3, c.notes)
        )
    }
}
