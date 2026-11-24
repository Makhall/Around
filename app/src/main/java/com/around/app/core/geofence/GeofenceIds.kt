package com.around.app.core.geofence

object GeofenceIds {
    private const val SEPARATOR = "|"

    fun requestId(circleId: String, placeId: String): String =
        "$circleId$SEPARATOR$placeId"

    fun parse(requestId: String): Pair<String, String>? {
        val index = requestId.indexOf(SEPARATOR)
        if (index <= 0 || index == requestId.lastIndex) {
            return null
        }
        return requestId.substring(0, index) to requestId.substring(index + 1)
    }
}
