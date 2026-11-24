package com.around.app.data.history

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val HISTORY_SAMPLE_INTERVAL_MS = 60_000L
const val HISTORY_SAMPLE_DISTANCE_METERS = 50f

fun shouldSampleHistory(
    lastTimestamp: Long,
    lastLatitude: Double,
    lastLongitude: Double,
    now: Long,
    latitude: Double,
    longitude: Double,
    intervalMs: Long = HISTORY_SAMPLE_INTERVAL_MS,
    distanceMeters: Float = HISTORY_SAMPLE_DISTANCE_METERS,
): Boolean {
    if (lastTimestamp <= 0L) {
        return true
    }
    if (now - lastTimestamp >= intervalMs) {
        return true
    }
    if (lastLatitude == 0.0 && lastLongitude == 0.0) {
        return true
    }
    return distanceMetersBetween(
        lastLatitude,
        lastLongitude,
        latitude,
        longitude,
    ) >= distanceMeters
}

private fun distanceMetersBetween(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Float {
    val earthRadius = 6_371_000.0
    val lat1 = Math.toRadians(fromLatitude)
    val lat2 = Math.toRadians(toLatitude)
    val deltaLat = Math.toRadians(toLatitude - fromLatitude)
    val deltaLng = Math.toRadians(toLongitude - fromLongitude)
    val a = sin(deltaLat / 2).pow(2) +
        cos(lat1) * cos(lat2) * sin(deltaLng / 2).pow(2)
    return (2 * earthRadius * asin(sqrt(a))).toFloat()
}
