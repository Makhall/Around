package com.around.app.core.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.model.Place
import com.around.app.data.places.PlaceRepository
import com.around.app.service.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class PlaceGeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val placeRepository: PlaceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    fun refresh() {
        val uid = authRepository.currentUid ?: return
        if (!hasLocationPermission()) {
            return
        }

        scope.launch {
            runCatching {
                val circles = circleRepository.getCircles(uid)
                val places = circles.flatMap { circle ->
                    placeRepository.getPlaces(circle.id)
                }
                register(places.take(MAX_GEOFENCES))
            }.onFailure { error ->
                Log.w(TAG, "Could not refresh geofences", error)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun register(places: List<Place>) {
        val pendingIntent = pendingIntent()
        geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
            if (places.isEmpty()) {
                Log.i(TAG, "Cleared geofences")
                return@addOnCompleteListener
            }

            val geofences = places.map { place ->
                Geofence.Builder()
                    .setRequestId(GeofenceIds.requestId(place.circleId, place.id))
                    .setCircularRegion(
                        place.latitude,
                        place.longitude,
                        place.radiusMeters.toFloat(),
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_EXIT,
                    )
                    .setNotificationResponsiveness(GEOFENCE_RESPONSIVENESS_MS)
                    .build()
            }

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofences(geofences)
                .build()

            geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Registered ${geofences.size} geofences")
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Could not add geofences", error)
                }
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, GeofenceBroadcastReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val background = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fine && background
    }

    companion object {
        const val MAX_GEOFENCES = 100
        private const val REQUEST_CODE = 2201
        private const val GEOFENCE_RESPONSIVENESS_MS = 5 * 60 * 1000
        private const val TAG = "PlaceGeofences"
    }
}
