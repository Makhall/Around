package com.around.app.feature.places

import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.around.app.R
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun PlacePickerMap(
    styleUrl: String,
    latitude: Double?,
    longitude: Double?,
    radiusMeters: Int,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pinHalfWidth = with(density) { 20.dp.toPx() }
    val pinHeight = with(density) { 40.dp.toPx() }
    val currentOnPick by rememberUpdatedState(onLocationPicked)
    val mapDescription = stringResource(R.string.place_map_content_description)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var styleLoaded by remember { mutableStateOf(false) }
    var styleFailed by remember { mutableStateOf(false) }
    var styleAttempt by remember { mutableIntStateOf(0) }
    var hasCentered by remember { mutableStateOf(false) }

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
                readyMap.addOnMapClickListener { latLng ->
                    currentOnPick(latLng.latitude, latLng.longitude)
                    true
                }
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

    LaunchedEffect(map, latitude, longitude) {
        val readyMap = map ?: return@LaunchedEffect
        if (hasCentered || latitude == null || longitude == null) {
            return@LaunchedEffect
        }
        hasCentered = true
        readyMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.0),
            600,
        )
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

        val selected = if (latitude != null && longitude != null && mapSize != IntSize.Zero) {
            remember(latitude, longitude, cameraTick, mapSize, map) {
                val readyMap = map ?: return@remember null
                runCatching {
                    readyMap.projection.toScreenLocation(LatLng(latitude, longitude))
                }.getOrNull()?.let { point ->
                    val metersPerPixel = runCatching {
                        readyMap.projection.getMetersPerPixelAtLatitude(latitude)
                    }.getOrDefault(1.0)
                    val radiusPx = (radiusMeters / metersPerPixel).toFloat()
                    SelectedPlace(Offset(point.x, point.y), radiusPx)
                }
            }
        } else {
            null
        }

        if (selected != null) {
            val primary = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primary.copy(alpha = 0.12f),
                    radius = selected.radiusPx,
                    center = selected.center,
                )
                drawCircle(
                    color = primary.copy(alpha = 0.65f),
                    radius = selected.radiusPx,
                    center = selected.center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .offset {
                        IntOffset(
                            (selected.center.x - pinHalfWidth).roundToInt(),
                            (selected.center.y - pinHeight).roundToInt(),
                        )
                    },
                shape = CircleShape,
                color = primary,
                contentColor = Color.White,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                    )
                }
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

private data class SelectedPlace(
    val center: Offset,
    val radiusPx: Float,
)
