package com.around.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.around.app.MainActivity
import com.around.app.R

object PlaceAlertNotifier {
    const val CHANNEL_ID = "place_alerts"

    fun show(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val actor = data["actorName"].orEmpty().ifBlank { "Someone" }
        val place = data["placeName"].orEmpty().ifBlank { "a place" }
        val circle = data["circleName"].orEmpty()
        val eventType = data["eventType"].orEmpty()
        val eventText = context.getString(
            if (eventType == "leave") {
                R.string.place_alert_leave
            } else {
                R.string.place_alert_arrive
            },
            actor,
            place,
        )
        val body = if (circle.isBlank()) {
            eventText
        } else {
            eventText + " · " + circle
        }

        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
            .putExtra("circleId", data["circleId"])
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            data["eventId"]?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(context.getString(R.string.place_alert_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                data["eventId"]?.hashCode() ?: System.currentTimeMillis().toInt(),
                notification,
            )
        } catch (_: SecurityException) {
            // Permission was revoked between the check and notify.
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.place_alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.place_alert_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
