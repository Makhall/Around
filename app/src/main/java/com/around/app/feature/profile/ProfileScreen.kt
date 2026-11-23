package com.around.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.core.designsystem.components.AvatarBadge
import com.around.app.data.model.Avatar
import com.around.app.data.model.AvatarType

private val emojiChoices = listOf("🙂", "😎", "🦊", "🐼", "🐧", "🌊", "🌙", "⭐")
private val colorChoices = listOf(
    "#006A6A",
    "#4B607C",
    "#8B5E3C",
    "#7A4EAB",
    "#B23A48",
    "#3B7A57",
)

@Composable
fun ProfileScreen(
    isOnboarding: Boolean,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var handledSaved by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved && !handledSaved) {
            handledSaved = true
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isOnboarding) {
                                R.string.profile_setup_title
                            } else {
                                R.string.profile_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarBadge(
                avatar = uiState.avatar,
                displayName = uiState.name.ifBlank { "?" },
                size = 88.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.profile_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_name_label)) },
                placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
                singleLine = true,
                isError = uiState.error == ProfileError.NAME_REQUIRED,
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.profile_avatar_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(emojiChoices) { emoji ->
                    val selected = uiState.avatar.type == AvatarType.EMOJI &&
                        uiState.avatar.value == emoji
                    Surface(
                        onClick = {
                            viewModel.onAvatarChange(Avatar(AvatarType.EMOJI, emoji))
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (selected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                colorChoices.forEach { hex ->
                    val color = remember(hex) {
                        Color(hex.toColorInt())
                    }
                    val selected = uiState.avatar.type == AvatarType.COLOR &&
                        uiState.avatar.value.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color, CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                },
                            )
                            .clickable {
                                viewModel.onAvatarChange(Avatar(AvatarType.COLOR, hex))
                            },
                    )
                }
            }

            val errorMessage = when (uiState.error) {
                ProfileError.NAME_REQUIRED -> stringResource(R.string.profile_error_name)
                ProfileError.LOAD_FAILED -> stringResource(R.string.profile_error_load)
                ProfileError.SAVE_FAILED -> stringResource(R.string.profile_error_save)
                null -> null
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (uiState.error == ProfileError.LOAD_FAILED) {
                    TextButton(onClick = viewModel::retryLoad) {
                        Text(stringResource(R.string.session_retry))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = viewModel::save,
                enabled = !uiState.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (uiState.saving) R.string.profile_saving else R.string.profile_save,
                    ),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
