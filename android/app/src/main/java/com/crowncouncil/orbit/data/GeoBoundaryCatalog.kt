package com.crowncouncil.orbit.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GeoPoint(val longitude: Double, val latitude: Double)

data class GeoBoundary(
    val upazilaId: String?,
    val name: String,
    val districtId: String?,
    val districtName: String?,
    val divisionId: String?,
    val divisionName: String?,
    val polygons: List<List<List<GeoPoint>>>
) {
    val hasAdministrativeMatch: Boolean
        get() = !districtId.isNullOrBlank() && !divisionId.isNullOrBlank()

    fun contains(longitude: Double, latitude: Double): Boolean =
        polygons.any { rings ->
            val outer = rings.firstOrNull() ?: return@any false
            pointInRing(longitude, latitude, outer) &&
                rings.drop(1).none { hole -> pointInRing(longitude, latitude, hole) }
        }

    private fun pointInRing(longitude: Double, latitude: Double, ring: List<GeoPoint>): Boolean {
        if (ring.size < 4) return false
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            val crosses = (a.latitude > latitude) != (b.latitude > latitude)
            if (crosses) {
                val crossingLongitude = (b.longitude - a.longitude) *
                    (latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
                if (longitude < crossingLongitude) inside = !inside
            }
            j = i
        }
        return inside
    }
}

/**
 * Offline ADM3 (upazila) GeoJSON reader. The source file is downloaded at build time
 * from a pinned geoBoundaries commit and bundled into the APK assets.
 */
object GeoBoundaryCatalog {
    const val ASSET_NAME = "bangladesh-upazilas.geojson"

    fun load(context: Context): List<GeoBoundary> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }
    }.getOrDefault(emptyList())

    fun parse(raw: String): List<GeoBoundary> {
        val root = JSONObject(raw)
        val features = root.optJSONArray("features") ?: JSONArray()
        return (0 until features.length()).mapNotNull { index ->
            runCatching {
                val feature = features.getJSONObject(index)
                val properties = feature.optJSONObject("properties") ?: JSONObject()
                val geometry = feature.optJSONObject("geometry") ?: return@runCatching null
                val coordinates = geometry.optJSONArray("coordinates") ?: return@runCatching null
                val polygons = when (geometry.optString("type")) {
                    "Polygon" -> listOf(parseRings(coordinates))
                    "MultiPolygon" -> (0 until coordinates.length()).mapNotNull { polygonIndex ->
                        parseRings(coordinates.getJSONArray(polygonIndex))
                    }
                    else -> emptyList()
                }.filter { polygon -> polygon.isNotEmpty() && polygon.first().size >= 4 }
                if (polygons.isEmpty()) return@runCatching null

                fun value(key: String): String? = properties.optString(key)
                    .takeIf { it.isNotBlank() && it != "null" }

                GeoBoundary(
                    upazilaId = value("upazila_id"),
                    name = value("name") ?: value("shapeName") ?: "Unknown upazila",
                    districtId = value("district_id"),
                    districtName = value("district_name"),
                    divisionId = value("division_id"),
                    divisionName = value("division_name"),
                    polygons = polygons
                )
            }.getOrNull()
        }
    }

    private fun parseRings(rings: JSONArray): List<List<GeoPoint>>? = runCatching {
        (0 until rings.length()).mapNotNull { ringIndex ->
            val coordinates = rings.getJSONArray(ringIndex)
            (0 until coordinates.length()).mapNotNull { pointIndex ->
                val pair = coordinates.getJSONArray(pointIndex)
                if (pair.length() < 2) null else {
                    val longitude = pair.getDouble(0)
                    val latitude = pair.getDouble(1)
                    if (!longitude.isFinite() || !latitude.isFinite()) null
                    else GeoPoint(longitude, latitude)
                }
            }
        }.filter { it.size >= 4 }
    }.getOrNull()
}
