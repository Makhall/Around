package com.around.app.feature.history

import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.around.app.R
import com.around.app.core.designsystem.components.AvatarBadge
import com.around.app.data.model.CircleMember
import com.around.app.data.model.HistoryPoint
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun HistoryMapView(
    points: List<HistoryPoint>,
    member: CircleMember?,
    styleUrl: String,
    fitKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val markerSizePx = with(density) { 40.dp.toPx() }
    val mapDescription = stringResource(R.string.history_map_content_description)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var styleLoaded by remember { mutableStateOf(false) }
    var styleFailed by remember { mutableStateOf(false) }
    var styleAttempt by remember { mutableIntStateOf(0) }
    var lastFitKey by remember { mutableStateOf<String?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            contentDescription = mapDescription
            getMapAsync { readyMap ->
                readyMap.uiSettings.isAttributionEnabled = true
                readyMap.uiSettings.isLogoEnabled = true
                readyMap.uiSettings.isCompassEnabled = false
                readyMap.uiSettings.isRotateGesturesEnabled = false
                readyMap.uiSettings.isTiltGesturesEnabled = false
                readyMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(0.0, 0.0))
                    .zoom(1.5)
                    .build()
                readyMap.addOnCameraMoveListener { cameraTick += 1 }
                map = readyMap
            }
            addOnDidFailLoadingMapListener { styleFailed = true }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            if (!mapView.isDestroyed) {
                runCatching {
                    mapView.onPause()
                    mapView.onStop()
                    mapView.onDestroy()
                }
            }
        }
    }

    LaunchedEffect(map, styleUrl, styleAttempt) {
        val readyMap = map ?: return@LaunchedEffect
        styleLoaded = false
        styleFailed = false
        readyMap.setStyle(styleUrl) { styleLoaded = true }
    }

    LaunchedEffect(map, points, fitKey) {
        val readyMap = map ?: return@LaunchedEffect
        if (points.isEmpty() || lastFitKey == fitKey) {
            return@LaunchedEffect
        }
        lastFitKey = fitKey
        if (points.size == 1) {
            val point = points.first()
            readyMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(point.latitude, point.longitude),
                    15.0,
                ),
                600,
            )
        } else {
            val bounds = LatLngBounds.Builder().apply {
                points.forEach { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            readyMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 80),
                700,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { mapSize = it },
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        val screenPoints = if (mapSize != IntSize.Zero) {
            remember(points, cameraTick, mapSize, map) {
                val readyMap = map ?: return@remember emptyList()
                if (mapSize.width == 0 || mapSize.height == 0) {
                    return@remember emptyList()
                }
                points.mapNotNull { point ->
                    if (!point.latitude.isFinite() || !point.longitude.isFinite()) {
                        return@mapNotNull null
                    }
                    runCatching {
                        readyMap.projection.toScreenLocation(
                            LatLng(point.latitude, point.longitude),
                        )
                    }.getOrNull()?.let { Offset(it.x, it.y) }
                }
            }
        } else {
            emptyList()
        }

        val trailColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (screenPoints.size >= 2) {
                val path = Path().apply {
                    moveTo(screenPoints.first().x, screenPoints.first().y)
                    screenPoints.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    path = path,
                    color = trailColor,
                    style = Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            screenPoints.firstOrNull()?.let {
                drawCircle(
                    color = Color(0xFF2E7D32),
                    radius = 7.dp.toPx(),
                    center = it,
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = it,
                )
            }
            screenPoints.lastOrNull()?.let {
                drawCircle(
                    color = trailColor,
                    radius = 9.dp.toPx(),
                    center = it,
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = it,
                )
            }
        }

        val lastPoint = screenPoints.lastOrNull()
        if (lastPoint != null && member != null) {
            val endDescription = stringResource(R.string.history_end_marker, member.displayName)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (lastPoint.x - markerSizePx / 2).roundToInt(),
                            (lastPoint.y - markerSizePx / 2).roundToInt(),
                        )
                    }
                    .size(44.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .border(2.dp, trailColor, CircleShape)
                    .semantics { contentDescription = endDescription },
                contentAlignment = Alignment.Center,
            ) {
                AvatarBadge(
                    avatar = member.avatar,
                    displayName = member.displayName,
                    size = 36.dp,
                    modifier = Modifier.background(Color.White, CircleShape),
                )
            }
        }

        if (!styleLoaded && !styleFailed) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 4.dp,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(14.dp).size(28.dp),
                    strokeWidth = 3.dp,
                )
            }
        }

        if (styleFailed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                tonalElevation = 4.dp,
            ) {
                Box(modifier = Modifier.padding(20.dp)) {
                    Text(text = stringResource(R.string.map_error))
                    TextButton(
                        onClick = { styleAttempt += 1 },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text(stringResource(R.string.map_retry))
                    }
                }
            }
        }
    }
}
