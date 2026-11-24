package com.around.app.feature.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

fun openDirections(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String,
) {
    val query = Uri.encode("$latitude,$longitude")
    val labelEncoded = Uri.encode(label)
    val geoUri = "geo:$latitude,$longitude?q=$query($labelEncoded)".toUri()
    val intent = Intent(Intent.ACTION_VIEW, geoUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            "https://www.google.com/maps/search/?api=1&query=$query".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(webIntent)
    }
}
