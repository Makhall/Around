package com.around.app.feature.circles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.core.designsystem.components.AroundBrandMark
import com.around.app.core.designsystem.components.AvatarBadge
import com.around.app.data.model.Circle

@Composable
fun CirclesScreen(
    onCreateCircle: () -> Unit,
    onJoinCircle: () -> Unit,
    onOpenCircle: (Circle) -> Unit,
    onEditProfile: () -> Unit,
    viewModel: CirclesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editProfileDescription = stringResource(R.string.edit_profile_content_description)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.circles_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    uiState.profile?.let { profile ->
                        IconButton(
                            onClick = onEditProfile,
                            modifier = Modifier.semantics {
                                contentDescription = editProfileDescription
                            },
                        ) {
                            AvatarBadge(
                                avatar = profile.avatar,
                                displayName = profile.displayName,
                                size = 36.dp,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.circles_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                uiState.error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.circles_error),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.circles_retry))
                        }
                    }
                }

                uiState.circles.isEmpty() -> {
                    EmptyCircles(
                        onCreateCircle = onCreateCircle,
                        onJoinCircle = onJoinCircle,
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onCreateCircle,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.circles_create))
                            }
                            OutlinedButton(
                                onClick = onJoinCircle,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.circles_join))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                bottom = 24.dp,
                            ),
                        ) {
                            items(uiState.circles, key = { it.id }) { circle ->
                                CircleCard(
                                    circle = circle,
                                    isOwner = circle.isOwnedBy(uiState.profile?.uid.orEmpty()),
                                    onClick = { onOpenCircle(circle) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCircles(
    onCreateCircle: () -> Unit,
    onJoinCircle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AroundBrandMark(size = 84.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.circles_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.circles_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onCreateCircle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.circles_create))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onJoinCircle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.circles_join))
        }
    }
}

@Composable
private fun CircleCard(
    circle: Circle,
    isOwner: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = circle.name.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = circle.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.circles_member_count,
                        circle.memberCount,
                        circle.memberCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isOwner) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.circles_owner),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
