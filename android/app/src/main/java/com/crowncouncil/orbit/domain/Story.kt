package com.crowncouncil.orbit.domain

import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.data.ItineraryRow
import com.crowncouncil.orbit.data.Trip
import com.crowncouncil.orbit.data.Visit
import java.util.Locale

/** A short shareable text story of a trip, built only from what the traveller entered. */
object Story {
    private val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    fun build(trip: Trip, dest: Destination?, rows: List<ItineraryRow>, spent: Int, visit: Visit?, badgesEarned: Int): String {
        val sb = StringBuilder()
        val people = if (trip.travellers == 1) "1 traveller" else "${trip.travellers} travellers"
        sb.append("My trip to ${trip.title}").append(if (dest != null) " (${dest.district})" else "").append('\n')
        sb.append("${trip.days} ${if (trip.days == 1) "day" else "days"} in ${months[(trip.startMonth - 1).coerceIn(0, 11)]}, $people, ${trip.tier.lowercase(Locale.ROOT)} tour.\n")
        for (day in 1..trip.days) {
            val items = rows.filter { it.dayNo == day }
            if (items.isNotEmpty()) sb.append("Day $day: ").append(items.joinToString("; ") { String.format(Locale.US, "%02d:00 %s", it.startHour, it.name) }).append('\n')
        }
        if (spent > 0) sb.append("Spent: BDT ").append(String.format(Locale.US, "%,d", spent)).append(" of BDT ").append(String.format(Locale.US, "%,d", trip.budget)).append(".\n")
        if (visit != null) {
            visit.rating?.let { sb.append("My rating: $it of 5.\n") }
            if (visit.note.isNotBlank()) sb.append("Memory: ${visit.note.trim()}\n")
            if (visit.source == "gps") sb.append("GPS-verified check-in.\n")
        }
        if (badgesEarned > 0) sb.append("Badges earned so far: $badgesEarned.\n")
        sb.append("Planned with ORBIT.")
        return sb.toString()
    }
}
