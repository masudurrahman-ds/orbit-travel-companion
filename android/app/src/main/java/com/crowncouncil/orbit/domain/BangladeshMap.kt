package com.crowncouncil.orbit.domain

/**
 * Stylised outline of Bangladesh (longitude, latitude), good enough to place district markers.
 * It is a simplified drawing, not a survey-grade border.
 */
object BangladeshMap {
    const val MIN_LON = 87.9
    const val MAX_LON = 92.8
    const val MIN_LAT = 20.5
    const val MAX_LAT = 26.7

    /** Width / height of the drawn map once longitude is scaled by cos(23.5 degrees). */
    val ASPECT: Float = ((MAX_LON - MIN_LON) * 0.917 / (MAX_LAT - MIN_LAT)).toFloat()

    val outline: List<Pair<Double, Double>> = listOf(
        88.08 to 26.00, 88.35 to 26.50, 88.70 to 26.40, 88.95 to 26.25, 89.30 to 26.08, 89.70 to 26.10,
        89.85 to 25.80, 89.85 to 25.30, 90.30 to 25.20, 90.70 to 25.15, 91.10 to 25.20, 91.60 to 25.15,
        92.00 to 25.10, 92.25 to 24.90, 92.10 to 24.50, 91.80 to 24.20, 91.65 to 23.90, 91.50 to 23.50,
        91.65 to 23.10, 91.80 to 23.00, 92.10 to 23.60, 92.35 to 23.30, 92.40 to 22.80, 92.45 to 22.20,
        92.30 to 21.70, 92.35 to 21.30, 92.25 to 20.90, 92.30 to 20.60, 92.05 to 21.15, 91.95 to 21.50,
        91.85 to 22.00, 91.80 to 22.35, 91.40 to 22.85, 91.05 to 22.75, 90.75 to 22.50, 90.70 to 22.10,
        90.35 to 21.95, 90.10 to 21.80, 89.80 to 21.80, 89.50 to 21.75, 89.15 to 21.85, 89.00 to 22.10,
        88.95 to 22.40, 88.80 to 22.80, 88.65 to 23.20, 88.70 to 23.60, 88.40 to 23.90, 88.20 to 24.30,
        88.05 to 24.65, 88.20 to 25.00, 88.45 to 25.40, 88.20 to 25.80
    )

    /** Maps a coordinate to 0..1 in both axes (y grows downward). */
    fun project(lon: Double, lat: Double): Pair<Float, Float> =
        (((lon - MIN_LON) / (MAX_LON - MIN_LON)).toFloat()) to ((1.0 - (lat - MIN_LAT) / (MAX_LAT - MIN_LAT)).toFloat())

    /** Great-circle distance in kilometres. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
