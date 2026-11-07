package com.crowncouncil.orbit.data

import android.content.Context
import org.json.JSONObject

/**
 * Read-only offline tourist marker catalogue imported from the user-provided KML.
 * The source records are not Room destinations: importing them must not silently
 * alter visit history, recommendation scores, prices, or itinerary records.
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
    private const val ASSET_NAME = "tourist-locations.geojson"

    fun load(context: Context): List<TouristLocation> = runCatching {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val features = root.getJSONArray("features")
        buildList {
            for (index in 0 until features.length()) {
                val feature = features.getJSONObject(index)
                val geometry = feature.getJSONObject("geometry")
                if (geometry.optString("type") != "Point") continue
                val coordinates = geometry.getJSONArray("coordinates")
                if (coordinates.length() < 2) continue
                val properties = feature.getJSONObject("properties")
                val longitude = coordinates.getDouble(0)
                val latitude = coordinates.getDouble(1)
                if (!latitude.isFinite() || !longitude.isFinite()) continue
                if (latitude !in 20.0..27.0 || longitude !in 87.0..93.0) continue
                val name = properties.optString("name").trim()
                if (name.isEmpty()) continue
                add(
                    TouristLocation(
                        id = "travel-mate-${index + 1}",
                        name = name,
                        sourceRegion = properties.optString("source_region"),
                        latitude = latitude,
                        longitude = longitude,
                        district = properties.optString("district").takeIf(String::isNotBlank),
                        division = properties.optString("division").takeIf(String::isNotBlank),
                        upazila = properties.optString("upazila").takeIf(String::isNotBlank),
                        administrativeMatchStatus = properties.optString("administrative_match_status")
                    )
                )
            }
        }.distinctBy { "${it.latitude},${it.longitude},${it.name}" }
    }.getOrDefault(emptyList())
}
