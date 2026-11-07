package com.crowncouncil.orbit.domain

import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.data.TouristLocation
import java.util.Locale

/**
 * Deterministic offline spatial retrieval over the bundled tourist catalogue and destination corpus.
 * This is a tool used by ORBIT Brain; it is not a trained language model.
 */
object GeoKnowledge {
    private data class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val district: String?,
        val division: String?,
        val upazila: String?
    )

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace("’", "")
            .replace("'", "")
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun catalogue(tourist: List<TouristLocation>, destinations: List<Destination>): List<Place> {
        val places = tourist.map {
            Place(it.name, it.latitude, it.longitude, it.district, it.division, it.upazila)
        } + destinations.map {
            Place(it.name, it.latitude, it.longitude, it.district, it.division, null)
        }
        return places.distinctBy { normalize(it.name) }
    }

    /** Returns null when this is not a geographic lookup request. */
    fun answer(
        utterance: String,
        tourist: List<TouristLocation>,
        destinations: List<Destination>,
        currentLocation: BrainLocation? = null
    ): String? {
        val text = normalize(utterance)
        val asksNearby = listOf("near", "nearby", "around", "within").any { text.contains(it) } ||
            text.contains("places close to")
        val asksInRegion = listOf("places in ", "attractions in ", "tourist spots in ", "show me places in ")
            .any { text.contains(it) }
        if (!asksNearby && !asksInRegion) return null

        val places = catalogue(tourist, destinations)
        if (places.isEmpty()) return "The offline geographic catalogue is not available on this device yet."

        if (asksInRegion) {
            val region = places.asSequence()
                .flatMap { listOfNotNull(it.district, it.division, it.upazila).asSequence() }
                .distinct()
                .sortedByDescending { normalize(it).length }
                .firstOrNull { text.contains(normalize(it)) }
                ?: return "I couldn't identify the district, division or upazila in that question. Try a specific administrative area name."
            val matches = places.filter {
                listOfNotNull(it.district, it.division, it.upazila).any { area -> normalize(area) == normalize(region) }
            }.distinctBy { normalize(it.name) }.take(12)
            if (matches.isEmpty()) return "I don't have verified tourist-place records for $region in the current offline catalogue."
            return "Places recorded for $region (${matches.size}${if (matches.size == 12) "+" else ""} shown): " +
                matches.joinToString("; ") { place ->
                    val sub = place.upazila?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                    place.name + sub
                } + ". Results come from ORBIT's bundled datasets; coverage is not exhaustive."
        }

        val radius = Regex("within (\\d{1,3}) km").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("(\\d{1,3}) km").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: 20.0
        val namedReference = places
            .sortedByDescending { normalize(it.name).length }
            .firstOrNull { text.contains(normalize(it.name)) }
        val reference = namedReference?.let { BrainLocation(it.latitude, it.longitude) } ?: currentLocation
            ?: return "To find nearby places, name a starting place or enable location access and share your current position with ORBIT."

        val originName = namedReference?.name ?: "your current location"
        val nearby = places.map { place ->
            place to BangladeshMap.distanceKm(reference.lat, reference.lon, place.latitude, place.longitude)
        }.filter { (place, distance) ->
            distance > 0.15 && distance <= radius && (namedReference == null || normalize(place.name) != normalize(namedReference.name))
        }.sortedBy { it.second }.take(8)

        if (nearby.isEmpty()) return "I couldn't find a listed place within ${radius.toInt()} km of $originName in the current offline catalogue. The catalogue may not include every attraction."
        return "Places within about ${radius.toInt()} km of $originName (straight-line distance): " +
            nearby.joinToString("; ") { (place, km) -> "${place.name} (${km.toInt()} km)" } +
            ". These are straight-line distances, not driving distances or travel times."
    }
}
