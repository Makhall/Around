package com.around.app.feature.circles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.core.designsystem.components.InviteCodeCard
import com.around.app.core.ui.userMessage
import kotlinx.coroutines.launch

@Composable
fun CreateCircleScreen(
    onBack: () -> Unit,
    onOpenCircle: (String) -> Unit,
    viewModel: CreateCircleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.invite_code_copied)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.create_circle_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            val created = uiState.created
            if (created == null) {
                Text(
                    text = stringResource(R.string.create_circle_name_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.create_circle_name_label)) },
                    placeholder = { Text(stringResource(R.string.create_circle_name_placeholder)) },
                    singleLine = true,
                    isError = uiState.error != null,
                )

                uiState.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::create,
                    enabled = !uiState.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.submitting) {
                                R.string.create_circle_creating
                            } else {
                                R.string.create_circle_action
                            },
                        ),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.create_circle_success_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_circle_success_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                InviteCodeCard(
                    code = created.inviteCode,
                    circleName = created.circleName,
                    onCopied = {
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    },
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onOpenCircle(created.circleId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.create_circle_open))
                }
            }
        }
    }
}
