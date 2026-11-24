package com.around.app.feature.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.around.app.R
import com.around.app.core.permissions.openAppSettings
import com.around.app.core.permissions.openBatteryOptimizationSettings
import com.around.app.core.permissions.rememberPermissionState

@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState()
    val status = permissionState.status

    val foregroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionState.refresh()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        permissionState.refresh()
    }

    val requestForeground = {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        foregroundLauncher.launch(permissions.toTypedArray())
    }

    val primaryAction = {
        when {
            !status.hasLocation -> requestForeground()
            !status.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                requestForeground()
            }
            !status.backgroundLocation && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            !status.backgroundLocation -> context.openAppSettings()
            else -> onDone()
        }
    }

    val primaryLabel = when {
        !status.hasLocation -> stringResource(R.string.permission_allow)
        !status.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            stringResource(R.string.permission_allow_notifications)
        !status.backgroundLocation -> stringResource(R.string.permission_allow_background)
        else -> stringResource(R.string.permission_continue)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.permission_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!status.locationServicesEnabled) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = stringResource(R.string.permission_location_services_off),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            PermissionRow(
                icon = Icons.Outlined.LocationOn,
                title = stringResource(R.string.permission_location_title),
                body = stringResource(R.string.permission_location_body),
                granted = status.hasLocation,
            )
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.permission_notifications_title),
                body = stringResource(R.string.permission_notifications_body),
                granted = status.notifications,
            )
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                icon = Icons.Outlined.MyLocation,
                title = stringResource(R.string.permission_background_title),
                body = stringResource(R.string.permission_background_body),
                granted = status.backgroundLocation,
            )

            if (!status.backgroundLocation) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            R.string.permission_background_settings_hint
                        } else {
                            R.string.permission_background_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = primaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryLabel)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { context.openAppSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.permission_open_settings))
            }

            Spacer(Modifier.height(24.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BatteryAlert,
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.map_battery_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.map_battery_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { context.openBatteryOptimizationSettings() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.map_battery_settings))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.permission_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (granted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (granted) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = stringResource(
                            if (granted) {
                                R.string.permission_status_granted
                            } else {
                                R.string.permission_status_missing
                            },
                        ),
                        tint = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
