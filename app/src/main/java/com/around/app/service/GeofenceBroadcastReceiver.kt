package com.around.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.around.app.core.geofence.GeofenceIds
import com.around.app.data.places.PlaceEventRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var placeEventRepository: PlaceEventRepository

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofence event error ${event.errorCode}")
            return
        }

        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "arrive"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "leave"
            else -> return
        }
        val geofences = event.triggeringGeofences ?: return
        if (geofences.isEmpty()) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                geofences.forEach { geofence ->
                    val (circleId, placeId) =
                        GeofenceIds.parse(geofence.requestId)
                            ?: return@forEach
                    placeEventRepository.sendEvent(circleId, placeId, type)
                        .onFailure { error ->
                            Log.w(TAG, "Could not send place event", error)
                        }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}
