package com.crowncouncil.orbit.domain

import com.crowncouncil.orbit.data.Destination
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

data class TransportInfo(val lines: List<String>, val footnote: String)

/**
 * "Getting there from Dhaka", built from the researched, dated fares in travel-costs.json (see the research audit).
 * Where a route fare was found it is shown as sourced; elsewhere it is a clearly labelled estimate:
 * BRTA's non-AC per-km rate times ORBIT's approximate road distance. Mirrors backend/src/transport.js.
 */
class TransportData(json: String) {
    private val root = JSONObject(json)
    private val entries = root.getJSONArray("entries").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun entry(id: String) = entries.first { it.getString("id") == id }
    private val low = entry("bus_nonac_perkm").getJSONObject("value").getDouble("taka_per_km")
    private val high = entry("bus_nonac_perkm_hike").getJSONObject("value").getDouble("taka_per_km")
    val checkedOn: String = root.getJSONObject("meta").getString("compiledOn")

    private fun tk(n: Int) = "Tk " + String.format(Locale.US, "%,d", n)
    private fun round10(v: Double) = (v / 10).roundToInt() * 10

    private val trainLabels = mapOf(
        "snigdha" to "Snigdha", "ac_seat" to "AC seat", "ac_berth" to "AC berth", "mail" to "mail train",
        "ac_snigdha_other_trains" to "AC Snigdha (other trains)", "snigdha_subarna_sonarbangla" to "Snigdha (Subarna / Sonar Bangla)"
    )

    fun describe(d: Destination): TransportInfo {
        if (d.distanceKm <= 10) return TransportInfo(listOf("Within Dhaka: use local transport."), "")
        val route = entries.firstOrNull { it.getString("category") == "transport" && it.optString("district") == d.district && it.getString("id").startsWith("route_") }
        val v = route?.getJSONObject("value")
        val lines = mutableListOf<String>()
        val bus = v?.optJSONObject("bus_nonac_tk")
        if (bus != null) lines += "Bus, non-AC (BRTA chart): ${tk(bus.getInt("seats51"))} on a 51-seat bus, ${tk(bus.getInt("seats40"))} on a 40-seat bus, one way from Dhaka."
        else lines += "Bus, non-AC (estimate): about ${tk(round10(d.distanceKm * low))} to ${tk(round10(d.distanceKm * high))} one way from Dhaka (BRTA rate Tk $low-$high per km x about ${d.distanceKm} km)."
        v?.optJSONObject("train_tk")?.let { t ->
            lines += "Train from Dhaka, one way: " + t.keys().asSequence().joinToString(", ") { k -> "${trainLabels[k] ?: k} ${tk(t.getInt(k))}" } + "."
        }
        if (v != null && v.has("air_oneway_from_tk")) lines += "Flights from about ${tk(v.getInt("air_oneway_from_tk"))} one way (fares change daily)."
        lines += "AC and premium coaches are not on the BRTA chart and usually cost more, especially in peak season."
        val level = when (route?.getString("confidence")) { "H" -> "high"; "M" -> "medium"; else -> "low" }
        return TransportInfo(lines, "Dated estimates checked $checkedOn, confidence $level. Confirm before you pay.")
    }
}
