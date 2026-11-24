package com.around.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object SharingServiceCommands {

    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, LocationForegroundService::class.java)
                .setAction(LocationForegroundService.ACTION_START),
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, LocationForegroundService::class.java)
                .setAction(LocationForegroundService.ACTION_STOP),
        )
    }

    fun pause(context: Context, until: Long?) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, LocationForegroundService::class.java)
                .setAction(LocationForegroundService.ACTION_PAUSE)
                .putExtra(
                    LocationForegroundService.EXTRA_PAUSE_UNTIL,
                    until ?: 0L,
                ),
        )
    }

    fun resume(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, LocationForegroundService::class.java)
                .setAction(LocationForegroundService.ACTION_RESUME),
        )
    }
}
