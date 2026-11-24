package com.around.app.data.model

data class Place(
    val id: String,
    val circleId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val createdBy: String,
    val createdAt: Long,
)

enum class PlaceError {
    NAME_REQUIRED,
    INVALID_RADIUS,
    LOCATION_REQUIRED,
    TOO_MANY_PLACES,
    NETWORK,
    UNKNOWN,
}

class PlaceOperationException(
    val error: PlaceError,
) : Exception(error.name)

fun Throwable.toPlaceError(): PlaceError =
    (this as? PlaceOperationException)?.error ?: PlaceError.UNKNOWN

const val MIN_PLACE_RADIUS_METERS = 100
const val MAX_PLACE_RADIUS_METERS = 1_000
const val MAX_PLACES_PER_CIRCLE = 20

fun Place.distanceTo(latitude: Double, longitude: Double): Double {
    val earthRadius = 6_371_000.0
    val lat1 = Math.toRadians(this.latitude)
    val lat2 = Math.toRadians(latitude)
    val deltaLat = Math.toRadians(latitude - this.latitude)
    val deltaLng = Math.toRadians(longitude - this.longitude)
    val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
        kotlin.math.sin(deltaLng / 2).let { it * it }
    return 2 * earthRadius * kotlin.math.asin(kotlin.math.sqrt(a))
}

fun List<Place>.placeAt(latitude: Double, longitude: Double): Place? =
    firstOrNull { place ->
        place.distanceTo(latitude, longitude) <= place.radiusMeters
    }
