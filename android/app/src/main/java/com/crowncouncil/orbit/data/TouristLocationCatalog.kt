package com.crowncouncil.orbit.data

import android.content.Context

/**
 * Read-only offline tourist marker catalogue imported from the user-provided KML.
 * It is deliberately separate from Room destinations so it cannot silently alter
 * visit history, recommendation scores, prices, or itinerary records.
 */
data class TouristLocation(
    val id: String,
    val name: String,
    val sourceRegion: String,
    val latitude: Double,
    val longitude: Double,
    val district: String?,
    val division: String?,
    val upazila: String?,
    val administrativeMatchStatus: String
)

object TouristLocationCatalog {
    private const val ASSET_NAME = "tourist-locations.psv"

    /**
     * Pipe-separated UTF-8 asset. A malformed row is skipped rather than preventing
     * the rest of the offline catalogue from loading.
     */
    fun load(context: Context): List<TouristLocation> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val fields = line.split('|')
                if (fields.size != 9) return@mapNotNull null
                val latitude = fields[2].toDoubleOrNull() ?: return@mapNotNull null
                val longitude = fields[3].toDoubleOrNull() ?: return@mapNotNull null
                if (!latitude.isFinite() || !longitude.isFinite()) return@mapNotNull null
                if (latitude !in 20.0..27.0 || longitude !in 87.0..93.0) return@mapNotNull null
                val name = fields[1].trim()
                if (name.isEmpty()) return@mapNotNull null
                fun optional(index: Int): String? = fields[index].trim().takeIf { it.isNotEmpty() }
                TouristLocation(
                    id = fields[0].trim(),
                    name = name,
                    sourceRegion = fields[4].trim(),
                    latitude = latitude,
                    longitude = longitude,
                    district = optional(5),
                    division = optional(6),
                    upazila = optional(7),
                    administrativeMatchStatus = fields[8].trim()
                )
            }.toList().distinctBy { it.id }
        }
    }.getOrDefault(emptyList())
}
