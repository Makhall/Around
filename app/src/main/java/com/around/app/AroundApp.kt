package com.around.app

import android.app.Application
import com.around.app.core.firebase.BackendConfig
import com.around.app.core.geofence.PlaceGeofenceRegistrar
import com.around.app.data.devices.DeviceRegistrar
import com.around.app.service.PlaceAlertNotifier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.google.firebase.firestore.firestore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.maplibre.android.MapLibre

@HiltAndroidApp
class AroundApp : Application() {

    @Inject
    lateinit var deviceRegistrar: DeviceRegistrar

    @Inject
    lateinit var geofenceRegistrar: PlaceGeofenceRegistrar

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        PlaceAlertNotifier.ensureChannel(this)

        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            Firebase.auth.useEmulator(BackendConfig.EMULATOR_HOST, 9099)
            Firebase.firestore.useEmulator(BackendConfig.EMULATOR_HOST, 8080)
            Firebase.database.useEmulator(BackendConfig.EMULATOR_HOST, 9000)
        }

        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                deviceRegistrar.registerCurrentDevice()
                geofenceRegistrar.refresh()
            }
        }
    }
}
