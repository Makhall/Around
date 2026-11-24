package com.around.app.feature.map

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.core.designsystem.components.AvatarBadge
import com.around.app.core.permissions.openBatteryOptimizationSettings
import com.around.app.core.permissions.rememberPermissionState
import com.around.app.core.ui.userMessage
import com.around.app.data.model.MemberRole
import com.around.app.service.SharingRuntime
import com.around.app.service.SharingServiceCommands
import com.around.app.service.SharingStatus
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleMapScreen(
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: CircleMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sharingStatus by SharingRuntime.status.collectAsStateWithLifecycle()
    val permissions = rememberPermissionState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val waitingLocationText = stringResource(R.string.map_waiting_location)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }
    var showPauseMenu by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = System.currentTimeMillis()
        }
    }

    val selectedMember = uiState.members.firstOrNull {
        it.member.uid == selectedUid
    }

    val sharing = uiState.sharing
    val sharingAllowed = uiState.sharingAllowed
    val canShare = permissions.status.hasLocation && permissions.status.notifications

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = uiState.circle?.name ?: stringResource(R.string.circle_detail_map),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    val circleId = uiState.circle?.id
                    if (circleId != null) {
                        IconButton(onClick = { onOpenHistory(circleId) }) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = stringResource(R.string.history_open),
                            )
                        }
                    }
                    IconButton(onClick = { showBatteryDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryAlert,
                            contentDescription = stringResource(R.string.map_battery_guidance),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MemberMap(
                members = uiState.members,
                currentUid = uiState.currentUid,
                selectedUid = selectedUid,
                styleUrl = uiState.mapStyleUrl,
                now = now,
                onSelectMember = { selectedUid = it },
                onMapClick = { selectedUid = null },
                onMapReady = { map = it },
                modifier = Modifier.fillMaxSize(),
            )

            if (uiState.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SharingCard(
                    sharing = sharing,
                    sharingAllowed = sharingAllowed,
                    sharingUpdating = uiState.sharingUpdating,
                    status = sharingStatus,
                    hasLocationPermission = permissions.status.hasLocation,
                    onToggleSharing = { checked ->
                        when {
                            !sharingAllowed -> Unit
                            checked && !canShare -> onOpenPermissions()
                            checked -> {
                                viewModel.setSharing(true)
                                SharingServiceCommands.start(context)
                            }
                            else -> viewModel.setSharing(false)
                        }
                    },
                    onStart = {
                        if (canShare) {
                            SharingServiceCommands.start(context)
                        } else {
                            onOpenPermissions()
                        }
                    },
                    onResume = { SharingServiceCommands.resume(context) },
                    onPause = { until ->
                        SharingServiceCommands.pause(context, until)
                        showPauseMenu = false
                    },
                    onStop = { SharingServiceCommands.stop(context) },
                    showPauseMenu = showPauseMenu,
                    onShowPauseMenu = { showPauseMenu = it },
                )

                if (!permissions.status.hasLocation || !permissions.status.notifications) {
                    PermissionBanner(
                        text = stringResource(
                            if (!permissions.status.hasLocation) {
                                R.string.map_permission_needed
                            } else {
                                R.string.map_notifications_needed
                            },
                        ),
                        action = stringResource(R.string.map_permission_action),
                        onAction = onOpenPermissions,
                    )
                } else if (!permissions.status.backgroundLocation && sharing) {
                    PermissionBanner(
                        text = stringResource(R.string.map_permission_background_warning),
                        action = stringResource(R.string.map_permission_action),
                        onAction = onOpenPermissions,
                    )
                }

                uiState.error?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = error.userMessage(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::retry) {
                                Text(stringResource(R.string.map_retry))
                            }
                        }
                    }
                }
            }

            if (uiState.removedFromCircle && !uiState.loading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                ) {
                    Text(
                        text = stringResource(R.string.circle_detail_gone),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            ) {
                Text(
                    text = stringResource(R.string.map_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            FloatingActionButton(
                onClick = {
                    val location = uiState.currentMember?.location
                    if (map != null && location != null) {
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude),
                                DEFAULT_ZOOM,
                            ),
                            500,
                        )
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(waitingLocationText)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MyLocation,
                    contentDescription = stringResource(R.string.map_center_on_me),
                )
            }
        }
    }

    selectedMember?.let { member ->
        ModalBottomSheet(
            onDismissRequest = { selectedUid = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            MemberSheet(
                member = member,
                currentUid = uiState.currentUid,
                now = now,
                onCenter = {
                    val location = member.location
                    if (map != null && location != null) {
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude),
                                DEFAULT_ZOOM,
                            ),
                            500,
                        )
                    }
                    selectedUid = null
                },
            )
        }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.map_battery_title)) },
            text = { Text(stringResource(R.string.map_battery_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        context.openBatteryOptimizationSettings()
                    },
                ) {
                    Text(stringResource(R.string.map_battery_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text(stringResource(R.string.map_close))
                }
            },
        )
    }
}

@Composable
private fun SharingCard(
    sharing: Boolean,
    sharingAllowed: Boolean,
    sharingUpdating: Boolean,
    status: SharingStatus,
    hasLocationPermission: Boolean,
    onToggleSharing: (Boolean) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onPause: (Long?) -> Unit,
    onStop: () -> Unit,
    showPauseMenu: Boolean,
    onShowPauseMenu: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val pauseLabel = when {
        status is SharingStatus.Paused && status.until != null && status.until != Long.MAX_VALUE ->
            stringResource(
                R.string.map_paused_until,
                DateFormat.getTimeFormat(context).format(Date(status.until)),
            )
        status is SharingStatus.Paused -> stringResource(R.string.map_paused_indefinitely)
        status is SharingStatus.Running -> stringResource(R.string.map_sharing_on)
        else -> stringResource(R.string.map_sharing_off)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.map_share_this_circle),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = when {
                            !sharingAllowed -> stringResource(R.string.map_sharing_disabled)
                            !hasLocationPermission -> stringResource(R.string.map_permission_needed)
                            !sharing -> stringResource(R.string.map_sharing_off)
                            else -> pauseLabel
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sharing,
                    onCheckedChange = onToggleSharing,
                    enabled = sharingAllowed && !sharingUpdating,
                )
            }

            if (sharing && sharingAllowed) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (status) {
                        is SharingStatus.Running -> {
                            Box {
                                TextButton(onClick = { onShowPauseMenu(true) }) {
                                    Text(stringResource(R.string.map_pause))
                                }
                                DropdownMenu(
                                    expanded = showPauseMenu,
                                    onDismissRequest = { onShowPauseMenu(false) },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.map_pause_1h)) },
                                        onClick = {
                                            onPause(System.currentTimeMillis() + 60 * 60 * 1000L)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.map_pause_8h)) },
                                        onClick = {
                                            onPause(
                                                System.currentTimeMillis() +
                                                    8 * 60 * 60 * 1000L,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.map_pause_indefinitely))
                                        },
                                        onClick = { onPause(null) },
                                    )
                                }
                            }
                            TextButton(onClick = onStop) {
                                Text(
                                    text = stringResource(R.string.map_stop_sharing),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        is SharingStatus.Paused -> {
                            TextButton(onClick = onResume) {
                                Text(stringResource(R.string.map_resume))
                            }
                            TextButton(onClick = onStop) {
                                Text(
                                    text = stringResource(R.string.map_stop_sharing),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        SharingStatus.Stopped -> {
                            TextButton(onClick = onStart) {
                                Text(stringResource(R.string.map_start_sharing))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) {
                Text(action)
            }
        }
    }
}

@Composable
private fun MemberSheet(
    member: MapMember,
    currentUid: String?,
    now: Long,
    onCenter: () -> Unit,
) {
    val context = LocalContext.current
    val location = member.location
    val lastSeen = if (location != null) {
        rememberRelativeTime(location.lastSeen)
    } else {
        stringResource(R.string.map_no_location)
    }
    val stale = location?.isStale(now) == true
    val roleLabel = stringResource(
        if (member.member.role == MemberRole.OWNER) {
            R.string.circles_owner
        } else {
            R.string.circles_member
        },
    )
    val memberSubtitle = if (member.member.uid == currentUid) {
        roleLabel + " · " + stringResource(R.string.circle_detail_you)
    } else {
        roleLabel
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(
                avatar = member.member.avatar,
                displayName = member.member.displayName,
                size = 56.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.member.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = memberSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stale) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.map_stale_chip),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (location == null) {
            Text(
                text = if (member.member.sharing) {
                    stringResource(R.string.map_waiting_location)
                } else {
                    stringResource(R.string.map_member_not_sharing)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DetailRow(
                icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                label = stringResource(R.string.map_last_updated, lastSeen),
            )
            location.batteryPercent?.let { battery ->
                Spacer(Modifier.height(8.dp))
                DetailRow(
                    icon = { Icon(Icons.Outlined.BatteryFull, contentDescription = null) },
                    label = stringResource(R.string.map_battery, battery),
                    trailing = if (location.charging == true) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    stringResource(R.string.map_charging),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            location.accuracyMeters?.let { accuracy ->
                Spacer(Modifier.height(8.dp))
                DetailRow(
                    icon = { Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null) },
                    label = stringResource(
                        R.string.map_accuracy,
                        accuracy.toInt(),
                    ),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = {
                location?.let {
                    openDirections(
                        context = context,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        label = member.member.displayName,
                    )
                }
            },
            enabled = location != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Directions, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.map_directions))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onCenter,
            enabled = location != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    R.string.map_center_on_member,
                    member.member.displayName,
                ),
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    label: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
