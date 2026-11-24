package com.around.app.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

data class PermissionStatus(
    val preciseLocation: Boolean = false,
    val coarseLocation: Boolean = false,
    val notifications: Boolean = false,
    val backgroundLocation: Boolean = false,
    val locationServicesEnabled: Boolean = true,
) {
    val hasLocation: Boolean
        get() = preciseLocation || coarseLocation

    val allGranted: Boolean
        get() = hasLocation && notifications && backgroundLocation
}

@Stable
class PermissionState internal constructor(context: Context) {
    private val appContext = context.applicationContext

    var status by mutableStateOf(readStatus(appContext))
        private set

    fun refresh() {
        status = readStatus(appContext)
    }

    private fun readStatus(context: Context): PermissionStatus = PermissionStatus(
        preciseLocation = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocation = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
        notifications = context.notificationsEnabled(),
        backgroundLocation = context.hasBackgroundLocation(),
        locationServicesEnabled = context.locationServicesEnabled(),
    )
}

@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    val state = remember(context) { PermissionState(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.refresh()
    }
    return state
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasForegroundLocation(): Boolean =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.notificationsEnabled(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
    return NotificationManagerCompat.from(this).areNotificationsEnabled()
}

fun Context.hasBackgroundLocation(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return hasForegroundLocation()
    }
    return hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
}

fun Context.locationServicesEnabled(): Boolean {
    val manager = getSystemService(LocationManager::class.java) ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.openBatteryOptimizationSettings() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
        .onFailure { openAppSettings() }
}
