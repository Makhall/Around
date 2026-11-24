package com.around.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.around.app.MainActivity
import com.around.app.R
import com.around.app.core.permissions.notificationsEnabled
import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.config.AppConfigRepository
import com.around.app.data.history.HistoryRepository
import com.around.app.data.history.shouldSampleHistory
import com.around.app.data.location.LocationRepository
import com.around.app.data.model.HistoryPoint
import com.around.app.data.model.LocationPayload
import com.around.app.data.sharing.SharingPreferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LocationForegroundService : Service() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var circleRepository: CircleRepository

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var preferences: SharingPreferences

    @Inject
    lateinit var configRepository: AppConfigRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var locationCallback: LocationCallback? = null
    private var sharingJob: Job? = null
    private var pauseJob: Job? = null

    @Volatile
    private var targetCircles: Set<String> = emptySet()

    @Volatile
    private var historyCircles: Set<String> = emptySet()

    @Volatile
    private var allCircleIds: Set<String> = emptySet()

    private var lastHistoryAt = 0L
    private var lastHistoryLatitude = 0.0
    private var lastHistoryLongitude = 0.0

    @Volatile
    private var stopping = false

    @Volatile
    private var stopGeneration = 0

    private var isMoving = true
    private var lastMovementAt = 0L
    private var currentStatus: SharingStatus = SharingStatus.Stopped

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSharing()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                ensureForeground()
                val duration = intent.getLongExtra(EXTRA_PAUSE_DURATION_MS, 0L)
                    .takeIf { it > 0L }
                val until = duration
                    ?.let { System.currentTimeMillis() + it }
                    ?: intent.getLongExtra(EXTRA_PAUSE_UNTIL, 0L).takeIf { it > 0L }
                pauseSharing(until)
            }

            ACTION_RESUME -> {
                ensureForeground()
                resumeSharing()
            }

            ACTION_START -> {
                ensureForeground()
                startRunning()
            }

            ACTION_RESTORE, null -> {
                ensureForeground()
                restoreFromPreferences()
            }

            else -> {
                ensureForeground()
                restoreFromPreferences()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeLocationUpdates()
        serviceScope.cancel()
        SharingRuntime.set(SharingStatus.Stopped)
        super.onDestroy()
    }

    private fun ensureForeground() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start foreground service", error)
            stopSelf()
        }
    }

    private fun startRunning() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission missing; stopping sharing")
            stopSharing()
            return
        }
        if (!notificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; stopping sharing")
            stopSharing()
            return
        }

        stopping = false
        stopGeneration += 1
        lastHistoryAt = 0L
        currentStatus = SharingStatus.Running(System.currentTimeMillis())
        SharingRuntime.set(currentStatus)
        serviceScope.launch {
            preferences.setSharingEnabled(true)
            preferences.setPausedUntil(0L)
        }
        serviceScope.launch {
            if (!configRepository.load().sharingEnabled) {
                Log.i(TAG, "Sharing disabled by Remote Config")
                stopSharing()
            }
        }

        observeTargets()
        startLocationUpdates()
        updateNotification()
    }

    private fun restoreFromPreferences() {
        serviceScope.launch {
            val snapshot = preferences.snapshot()
            val now = System.currentTimeMillis()
            when {
                !snapshot.enabled -> stopSharing()

                snapshot.pausedUntil == Long.MAX_VALUE || snapshot.pausedUntil > now -> {
                    pauseSharing(
                        snapshot.pausedUntil.takeIf {
                            it != Long.MAX_VALUE && it > now
                        },
                    )
                }

                else -> startRunning()
            }
        }
    }

    private fun pauseSharing(until: Long?) {
        removeLocationUpdates()
        pauseJob?.cancel()

        currentStatus = SharingStatus.Paused(until)
        SharingRuntime.set(currentStatus)
        serviceScope.launch {
            preferences.setPausedUntil(until ?: Long.MAX_VALUE)
        }
        updateNotification()

        val now = System.currentTimeMillis()
        if (until != null && until > now) {
            pauseJob = serviceScope.launch {
                delay(until - now)
                resumeSharing()
            }
        } else if (until != null) {
            resumeSharing()
        }
    }

    private fun resumeSharing() {
        if (!hasLocationPermission()) {
            stopSharing()
            return
        }

        pauseJob?.cancel()
        currentStatus = SharingStatus.Running(System.currentTimeMillis())
        SharingRuntime.set(currentStatus)
        serviceScope.launch { preferences.setPausedUntil(0L) }
        startLocationUpdates()
        updateNotification()
    }

    private fun stopSharing() {
        if (stopping) {
            return
        }
        stopping = true
        val generation = ++stopGeneration

        removeLocationUpdates()
        sharingJob?.cancel()
        pauseJob?.cancel()
        currentStatus = SharingStatus.Stopped
        SharingRuntime.set(currentStatus)
        targetCircles = emptySet()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        val uid = authRepository.currentUid
        val knownCircles = allCircleIds

        serviceScope.launch {
            preferences.setSharingEnabled(false)
            preferences.setPausedUntil(0L)

            if (generation != stopGeneration) {
                return@launch
            }

            if (uid != null) {
                val circles = knownCircles.ifEmpty {
                    runCatching {
                        circleRepository.observeCircles(uid)
                            .first()
                            .map { it.id }
                            .toSet()
                    }.getOrDefault(emptySet())
                }
                if (circles.isNotEmpty()) {
                    circles.forEach { circleId ->
                        runCatching {
                            circleRepository.setSharing(circleId, uid, false)
                        }.onFailure {
                            Log.w(TAG, "Could not disable sharing for $circleId", it)
                        }
                    }
                    locationRepository.clearLocations(circles, uid)
                        .onFailure {
                            Log.w(TAG, "Could not clear shared locations", it)
                        }
                }
            }

            if (generation != stopGeneration) {
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (generation == stopGeneration) {
                    stopSelf()
                }
            }
        }
    }

    private fun observeTargets() {
        sharingJob?.cancel()
        val uid = authRepository.currentUid ?: return

        sharingJob = serviceScope.launch {
            observeSharingCircles(uid).collect { targets ->
                targetCircles = targets.live
                val hadHistoryCircles = historyCircles.isNotEmpty()
                historyCircles = targets.history
                if (!hadHistoryCircles && historyCircles.isNotEmpty()) {
                    lastHistoryAt = 0L
                }
                updateNotification()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSharingCircles(uid: String): Flow<SharingTargets> =
        circleRepository.observeCircles(uid).flatMapLatest { circles ->
            allCircleIds = circles.map { it.id }.toSet()
            if (circles.isEmpty()) {
                flowOf(SharingTargets())
            } else {
                val flows = circles.map { circle ->
                    circleRepository.observeMembers(circle.id)
                        .map { members ->
                            val me = members.firstOrNull { it.uid == uid }
                            if (me?.sharing == true) {
                                CircleTarget(circle.id, me.historyEnabled)
                            } else {
                                null
                            }
                        }
                        .catch { emit(null) }
                }
                combine(flows) { targets ->
                    val active = targets.filterNotNull()
                    SharingTargets(
                        live = active.map { it.circleId }.toSet(),
                        history = active
                            .filter { it.history }
                            .map { it.circleId }
                            .toSet(),
                    )
                }
            }
        }

    private fun startLocationUpdates() {
        if (locationCallback != null) {
            return
        }
        if (!hasLocationPermission()) {
            stopSharing()
            return
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::onLocation)
            }
        }
        locationCallback = callback
        isMoving = true
        lastMovementAt = System.currentTimeMillis()
        requestUpdates(moving = true)
    }

    private fun requestUpdates(moving: Boolean) {
        val callback = locationCallback ?: return
        runCatching { fusedLocationClient.removeLocationUpdates(callback) }

        val request = if (moving) {
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                MOVING_INTERVAL_MS,
            )
                .setMaxUpdateDelayMillis(MOVING_INTERVAL_MS * 2)
                .build()
        } else {
            LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                STATIONARY_INTERVAL_MS,
            ).build()
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper(),
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Location permission revoked", error)
            stopSharing()
        }
    }

    private fun onLocation(location: Location) {
        val speed = if (location.hasSpeed()) location.speed else null
        val now = System.currentTimeMillis()

        if (speed != null && speed > 1f) {
            lastMovementAt = now
            if (!isMoving) {
                isMoving = true
                requestUpdates(moving = true)
            }
        } else if (isMoving && now - lastMovementAt > STATIONARY_AFTER_MS) {
            isMoving = false
            requestUpdates(moving = false)
        }

        val battery = batteryState()
        val uid = authRepository.currentUid ?: return
        val circles = targetCircles
        if (circles.isEmpty()) {
            return
        }

        val payload = LocationPayload(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            speedMps = speed,
            batteryPercent = battery.first,
            charging = battery.second,
            fixTime = location.time.takeIf { it > 0L } ?: now,
        )

        val historyTargets = historyCircles
        val historyPoint = if (
            historyTargets.isNotEmpty() &&
            shouldSampleHistory(
                lastTimestamp = lastHistoryAt,
                lastLatitude = lastHistoryLatitude,
                lastLongitude = lastHistoryLongitude,
                now = now,
                latitude = location.latitude,
                longitude = location.longitude,
            )
        ) {
            lastHistoryAt = now
            lastHistoryLatitude = location.latitude
            lastHistoryLongitude = location.longitude
            HistoryPoint(
                uid = uid,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = payload.accuracyMeters,
                timestamp = payload.fixTime,
            )
        } else {
            null
        }

        serviceScope.launch {
            locationRepository.writeLocation(circles, uid, payload)
                .onFailure { Log.w(TAG, "Failed to write location", it) }
            historyPoint?.let { point ->
                historyRepository.appendHistory(historyTargets, uid, point)
                    .onFailure { Log.w(TAG, "Failed to write history", it) }
            }
        }
    }

    private data class CircleTarget(
        val circleId: String,
        val history: Boolean,
    )

    private data class SharingTargets(
        val live: Set<String> = emptySet(),
        val history: Set<String> = emptySet(),
    )

    private fun batteryState(): Pair<Int?, Boolean?> {
        val manager = getSystemService(BATTERY_SERVICE) as? BatteryManager
            ?: return null to null
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        return percent to manager.isCharging
    }

    private fun removeLocationUpdates() {
        val callback = locationCallback ?: return
        runCatching { fusedLocationClient.removeLocationUpdates(callback) }
        locationCallback = null
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateNotification() {
        if (currentStatus == SharingStatus.Stopped) {
            return
        }
        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification())
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to update notification", error)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_location)
        .setContentTitle(notificationTitle())
        .setContentText(notificationText())
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            }
            when (currentStatus) {
                is SharingStatus.Paused -> {
                    addAction(
                        0,
                        getString(R.string.sharing_notification_action_resume),
                        serviceAction(ACTION_RESUME, REQUEST_RESUME),
                    )
                }

                else -> {
                    addAction(
                        0,
                        getString(R.string.sharing_notification_action_pause),
                        pauseAction(),
                    )
                }
            }
            addAction(
                0,
                getString(R.string.sharing_notification_action_stop),
                serviceAction(ACTION_STOP, REQUEST_STOP),
            )
        }
        .build()

    private fun notificationTitle(): String = when (currentStatus) {
        is SharingStatus.Paused -> getString(R.string.sharing_notification_paused_title)
        is SharingStatus.Running -> getString(R.string.sharing_notification_title)
        SharingStatus.Stopped -> getString(R.string.sharing_notification_preparing)
    }

    private fun notificationText(): String = when (val status = currentStatus) {
        is SharingStatus.Paused -> {
            val until = status.until
            if (until == null) {
                getString(R.string.sharing_notification_paused_indefinitely)
            } else {
                getString(
                    R.string.sharing_notification_paused_until,
                    DateFormat.getTimeFormat(this).format(Date(until)),
                )
            }
        }

        is SharingStatus.Running -> {
            if (targetCircles.isEmpty()) {
                getString(R.string.sharing_notification_no_circles)
            } else {
                resources.getQuantityString(
                    R.plurals.sharing_notification_text,
                    targetCircles.size,
                    targetCircles.size,
                )
            }
        }

        SharingStatus.Stopped -> getString(R.string.sharing_notification_preparing)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pauseAction(): PendingIntent = PendingIntent.getService(
        this,
        REQUEST_PAUSE,
        Intent(this, LocationForegroundService::class.java)
            .setAction(ACTION_PAUSE)
            .putExtra(EXTRA_PAUSE_DURATION_MS, ONE_HOUR_MS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LocationForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sharing_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.sharing_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.around.app.action.START_SHARING"
        const val ACTION_STOP = "com.around.app.action.STOP_SHARING"
        const val ACTION_PAUSE = "com.around.app.action.PAUSE_SHARING"
        const val ACTION_RESUME = "com.around.app.action.RESUME_SHARING"
        const val ACTION_RESTORE = "com.around.app.action.RESTORE_SHARING"
        const val EXTRA_PAUSE_UNTIL = "pause_until"
        const val EXTRA_PAUSE_DURATION_MS = "pause_duration_ms"

        private const val TAG = "LocationSharing"
        private const val CHANNEL_ID = "sharing_status"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 10
        private const val REQUEST_PAUSE = 11
        private const val REQUEST_RESUME = 12
        private const val REQUEST_STOP = 13
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private const val MOVING_INTERVAL_MS = 10_000L
        private const val STATIONARY_INTERVAL_MS = 5 * 60 * 1000L
        private const val STATIONARY_AFTER_MS = 2 * 60 * 1000L
    }
}
