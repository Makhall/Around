package com.around.app.feature.history

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.core.designsystem.components.AvatarBadge
import com.around.app.core.ui.userMessage
import com.around.app.data.model.CircleMember
import com.around.app.data.model.HistoryPoint
import java.util.Date

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = uiState.circle?.name ?: stringResource(R.string.history_title),
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
                    IconButton(onClick = viewModel::retry) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.map_retry),
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
        if (uiState.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error.userMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.map_retry))
                        }
                    }
                }
            }

            MemberPicker(
                members = uiState.members,
                selectedUid = uiState.selectedUid,
                onSelect = viewModel::selectMember,
            )
            RangePicker(
                selectedHours = uiState.rangeHours,
                onSelect = viewModel::selectRange,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                HistoryMapView(
                    points = uiState.points,
                    member = uiState.selectedMember,
                    styleUrl = uiState.mapStyleUrl,
                    fitKey = "${uiState.selectedUid}-${uiState.rangeHours}",
                    modifier = Modifier.fillMaxSize(),
                )

                if (uiState.historyLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }

                if (uiState.points.isEmpty() && !uiState.historyLoading) {
                    EmptyHistoryCard(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
            }

            Timeline(
                points = uiState.points,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MemberPicker(
    members: List<CircleMember>,
    selectedUid: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 6.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(members, key = { it.uid }) { member ->
            FilterChip(
                selected = member.uid == selectedUid,
                onClick = { onSelect(member.uid) },
                label = {
                    Text(
                        text = member.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    AvatarBadge(
                        avatar = member.avatar,
                        displayName = member.displayName,
                        size = 24.dp,
                    )
                },
            )
        }
    }
}

@Composable
private fun RangePicker(
    selectedHours: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1, 6, 24).forEach { hours ->
            FilterChip(
                selected = selectedHours == hours,
                onClick = { onSelect(hours) },
                label = {
                    Text(
                        stringResource(
                            when (hours) {
                                1 -> R.string.history_range_1h
                                6 -> R.string.history_range_6h
                                else -> R.string.history_range_24h
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.history_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Timeline(
    points: List<HistoryPoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formatter = remember { DateFormat.getTimeFormat(context) }
    val ordered = remember(points) { points.sortedByDescending { it.timestamp } }

    Surface(
        modifier = modifier,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp, max = 226.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.history_timeline_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = pluralStringResource(
                        R.plurals.history_point_count,
                        ordered.size,
                        ordered.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (ordered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_timeline_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp),
                ) {
                    itemsIndexed(ordered) { index, point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatter.format(Date(point.timestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = when {
                                    index == 0 -> stringResource(R.string.history_latest)
                                    index == ordered.lastIndex -> {
                                        stringResource(R.string.history_start)
                                    }
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            point.accuracyMeters?.let { accuracy ->
                                Text(
                                    text = stringResource(
                                        R.string.map_accuracy,
                                        accuracy.toInt(),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
