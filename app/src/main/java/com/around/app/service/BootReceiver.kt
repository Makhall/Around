package com.around.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.around.app.core.geofence.PlaceGeofenceRegistrar
import com.around.app.data.sharing.SharingPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: SharingPreferences

    @Inject
    lateinit var geofenceRegistrar: PlaceGeofenceRegistrar

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                geofenceRegistrar.refresh()
                val snapshot = preferences.snapshot()
                if (!snapshot.enabled) {
                    return@launch
                }

                val serviceIntent = Intent(context, LocationForegroundService::class.java)
                    .setAction(LocationForegroundService.ACTION_RESTORE)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (error: Exception) {
                Log.w(TAG, "Could not restore location sharing after boot", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
