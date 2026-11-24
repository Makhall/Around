package com.around.app.feature.map

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.around.app.data.model.MemberLocation
import com.around.app.data.model.MemberLocation.Companion.STALE_THRESHOLD_MS
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private data class MarkerPosition(
    val member: MapMember,
    val location: MemberLocation,
    val screen: Offset,
)

@Composable
fun MemberMap(
    members: List<MapMember>,
    currentUid: String?,
    selectedUid: String?,
    styleUrl: String,
    now: Long,
    onSelectMember: (String) -> Unit,
    onMapClick: () -> Unit,
    onMapReady: (MapLibreMap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val markerSize = 44.dp
    val markerSizePx = with(density) { markerSize.toPx() }
    val mapDescription = stringResource(R.string.map_content_description)

    val currentOnSelect by rememberUpdatedState(onSelectMember)
    val currentOnClick by rememberUpdatedState(onMapClick)
    val currentOnReady by rememberUpdatedState(onMapReady)

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
                readyMap.addOnMapClickListener {
                    currentOnClick()
                    true
                }
                map = readyMap
                currentOnReady(readyMap)
            }
            addOnDidFailLoadingMapListener {
                styleFailed = true
            }
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

    LaunchedEffect(members, map) {
        val readyMap = map ?: return@LaunchedEffect
        if (hasCentered) {
            return@LaunchedEffect
        }
        val target = members.firstOrNull {
            it.member.uid == currentUid && it.location != null
        }?.location ?: members.firstOrNull { it.location != null }?.location
        if (target != null) {
            hasCentered = true
            readyMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(target.latitude, target.longitude),
                    DEFAULT_ZOOM,
                ),
                600,
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

        if (mapSize != IntSize.Zero) {
            val positions = remember(members, cameraTick, mapSize, map, markerSizePx) {
                val readyMap = map ?: return@remember emptyList()
                if (mapSize.width == 0 || mapSize.height == 0) {
                    return@remember emptyList()
                }
                members.mapNotNull { member ->
                    val location = member.location ?: return@mapNotNull null
                    if (!location.latitude.isFinite() || !location.longitude.isFinite()) {
                        return@mapNotNull null
                    }
                    val screen = runCatching {
                        readyMap.projection.toScreenLocation(
                            LatLng(location.latitude, location.longitude),
                        )
                    }.getOrNull() ?: return@mapNotNull null
                    MarkerPosition(member, location, Offset(screen.x, screen.y))
                }
            }

            val ordered = positions.sortedWith(
                compareBy(
                    { it.member.member.uid == currentUid },
                    { it.member.member.uid == selectedUid },
                ),
            )

            ordered.forEach { position ->
                val uid = position.member.member.uid
                val isCurrent = uid == currentUid
                val isSelected = uid == selectedUid
                val stale = position.location.isStale(now, STALE_THRESHOLD_MS)
                val lastSeen = rememberRelativeTime(position.location.lastSeen)
                val description = stringResource(
                    R.string.map_marker_content_description,
                    position.member.member.displayName,
                    lastSeen,
                )

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (position.screen.x - markerSizePx / 2).roundToInt(),
                                (position.screen.y - markerSizePx / 2).roundToInt(),
                            )
                        }
                        .size(48.dp)
                        .alpha(if (stale) 0.55f else 1f)
                        .clickable(
                            onClickLabel = stringResource(R.string.map_member_details),
                            onClick = { currentOnSelect(uid) },
                        )
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    CircleShape,
                                )
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ),
                        )
                    }
                    AvatarBadge(
                        avatar = position.member.member.avatar,
                        displayName = position.member.member.displayName,
                        size = 40.dp,
                        modifier = Modifier
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                            .border(
                                width = 2.dp,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                                shape = CircleShape,
                            ),
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
                        onClick = {
                            styleAttempt += 1
                        },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text(stringResource(R.string.map_retry))
                    }
                }
            }
        }
    }
}

const val DEFAULT_ZOOM = 15.0
