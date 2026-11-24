package com.around.app.data.model

data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMps: Float?,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val fixTime: Long,
)

data class MemberLocation(
    val uid: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMps: Float?,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val fixTime: Long,
    val serverTime: Long,
) {
    val lastSeen: Long
        get() = if (serverTime > 0L) serverTime else fixTime

    fun isStale(
        now: Long = System.currentTimeMillis(),
        thresholdMs: Long = STALE_THRESHOLD_MS,
    ): Boolean = lastSeen > 0L && now - lastSeen > thresholdMs

    companion object {
        const val STALE_THRESHOLD_MS = 15 * 60 * 1000L
    }
}
