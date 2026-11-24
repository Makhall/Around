package com.around.app.data.model

data class HistoryPoint(
    val uid: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestamp: Long,
)
