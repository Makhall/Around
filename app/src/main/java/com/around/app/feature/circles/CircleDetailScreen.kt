package com.around.app.feature.circles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.core.designsystem.components.AvatarBadge
import com.around.app.core.designsystem.components.InviteCodeCard
import com.around.app.core.permissions.rememberPermissionState
import com.around.app.core.ui.userMessage
import com.around.app.data.model.CircleMember
import com.around.app.data.model.MemberRole
import com.around.app.service.SharingRuntime
import com.around.app.service.SharingServiceCommands
import com.around.app.service.SharingStatus
import kotlinx.coroutines.launch

@Composable
fun CircleDetailScreen(
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenPlaces: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: CircleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sharingStatus by SharingRuntime.status.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permissions = rememberPermissionState()
    val copiedMessage = stringResource(R.string.invite_code_copied)
    var pendingRemove by remember { mutableStateOf<CircleMember?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var handledRemoved by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.removedFromCircle) {
        if (uiState.removedFromCircle && !handledRemoved) {
            handledRemoved = true
            onLeft()
        }
    }

    val circle = uiState.circle
    val isOwner = circle != null && circle.ownerUid == uiState.currentUid
    val currentMember = uiState.members.firstOrNull { it.uid == uiState.currentUid }
    val sharing = currentMember?.sharing == true
    val historyEnabled = currentMember?.historyEnabled == true
    val placeAlerts = currentMember?.placeAlerts == true

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = circle?.name ?: stringResource(R.string.circles_title),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.circle_detail_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        if (circle == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.circle_detail_gone),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error.userMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.circles_retry))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            ElevatedCard(
                onClick = { onOpenMap(circle.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.circle_detail_map),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.circle_detail_map_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            ElevatedCard(
                onClick = { onOpenHistory(circle.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.circle_detail_history_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.circle_detail_history_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            ElevatedCard(
                onClick = { onOpenPlaces(circle.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Place,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.circle_detail_places_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.circle_detail_places_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.circle_detail_sharing_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (!uiState.sharingAllowed) {
                                    stringResource(R.string.map_sharing_disabled)
                                } else {
                                    stringResource(R.string.circle_detail_sharing_body)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = sharing,
                            onCheckedChange = { checked ->
                                when {
                                    !uiState.sharingAllowed -> Unit
                                    checked && (
                                        !permissions.status.hasLocation ||
                                            !permissions.status.notifications
                                        ) -> onOpenPermissions()

                                    else -> {
                                        viewModel.setSharing(checked)
                                        if (checked) {
                                            SharingServiceCommands.start(context)
                                        }
                                    }
                                }
                            },
                            enabled = uiState.sharingAllowed && !uiState.sharingUpdating,
                        )
                    }

                    if (sharing && permissions.status.hasLocation &&
                        !permissions.status.notifications
                    ) {
                        Spacer(Modifier.height(10.dp))
                        SharingWarning(
                            text = stringResource(R.string.permission_notifications_body),
                            onOpenPermissions = onOpenPermissions,
                        )
                    }

                    if (sharing && permissions.status.hasLocation &&
                        !permissions.status.backgroundLocation
                    ) {
                        Spacer(Modifier.height(10.dp))
                        SharingWarning(
                            text = stringResource(
                                R.string.map_permission_background_warning,
                            ),
                            onOpenPermissions = onOpenPermissions,
                        )
                    }

                    if (sharing) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sharingStatus is SharingStatus.Stopped &&
                                permissions.status.hasLocation &&
                                permissions.status.notifications
                            ) {
                                TextButton(
                                    onClick = { SharingServiceCommands.start(context) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 0.dp,
                                    ),
                                ) {
                                    Text(stringResource(R.string.map_start_sharing))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { onOpenMap(circle.id) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 0.dp,
                                ),
                            ) {
                                Text(stringResource(R.string.circle_detail_open_map))
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.circle_detail_history_save),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.circle_detail_history_save_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = historyEnabled,
                            onCheckedChange = viewModel::setHistoryEnabled,
                            enabled = !uiState.historyUpdating && !uiState.sharingUpdating,
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.circle_detail_place_alerts),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.circle_detail_place_alerts_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = placeAlerts,
                            onCheckedChange = viewModel::setPlaceAlerts,
                            enabled = !uiState.placeAlertsUpdating,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(
                    R.string.circle_detail_members_count,
                    uiState.members.size,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    uiState.members.forEachIndexed { index, member ->
                        MemberRow(
                            member = member,
                            currentUid = uiState.currentUid,
                            isOwner = isOwner,
                            removing = uiState.removingUid != null,
                            onRemove = { pendingRemove = member },
                        )
                        if (index != uiState.members.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.circle_detail_invite_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.circle_detail_invite_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (isOwner) {
                        uiState.inviteCode?.let { invite ->
                            Spacer(Modifier.height(16.dp))
                            InviteCodeCard(
                                code = invite.code,
                                circleName = circle.name,
                                onCopied = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(copiedMessage)
                                    }
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        if (uiState.inviteCode == null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.invite_code_expiry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::generateInviteCode,
                            enabled = !uiState.generatingCode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = when {
                                    uiState.generatingCode -> stringResource(R.string.create_circle_creating)
                                    uiState.inviteCode == null -> stringResource(R.string.circle_detail_generate_code)
                                    else -> stringResource(R.string.circle_detail_new_code)
                                },
                            )
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.circle_detail_invite_owner_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (isOwner) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.circle_detail_owner_note),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { showLeaveDialog = true },
                    enabled = !uiState.leaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.leaving) {
                                R.string.circle_detail_leaving
                            } else {
                                R.string.circle_detail_leave
                            },
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    pendingRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = {
                Text(
                    stringResource(
                        R.string.circle_detail_remove_title,
                        member.displayName,
                    ),
                )
            },
            text = { Text(stringResource(R.string.circle_detail_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        viewModel.removeMember(member.uid)
                    },
                ) {
                    Text(stringResource(R.string.circle_detail_confirm_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.circle_detail_cancel))
                }
            },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = {
                Text(
                    stringResource(
                        R.string.circle_detail_leave_title,
                        circle?.name.orEmpty(),
                    ),
                )
            },
            text = { Text(stringResource(R.string.circle_detail_leave_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leaveCircle()
                    },
                ) {
                    Text(stringResource(R.string.circle_detail_confirm_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.circle_detail_cancel))
                }
            },
        )
    }
}

@Composable
private fun SharingWarning(
    text: String,
    onOpenPermissions: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenPermissions) {
                Text(stringResource(R.string.map_permission_action))
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: CircleMember,
    currentUid: String?,
    isOwner: Boolean,
    removing: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBadge(
            avatar = member.avatar,
            displayName = member.displayName,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.uid == currentUid) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.circle_detail_you),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    if (member.role == MemberRole.OWNER) {
                        R.string.circles_owner
                    } else {
                        R.string.circles_member
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isOwner && member.uid != currentUid) {
            IconButton(
                onClick = onRemove,
                enabled = !removing,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonRemove,
                    contentDescription = stringResource(
                        R.string.remove_member_content_description,
                        member.displayName,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
