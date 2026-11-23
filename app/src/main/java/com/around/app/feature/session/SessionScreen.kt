package com.around.app.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.core.designsystem.components.AroundBrandMark

@Composable
fun SessionScreen(
    onReady: () -> Unit,
    onNeedsProfile: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var handled by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (handled) {
            return@LaunchedEffect
        }
        when (uiState) {
            SessionUiState.Ready -> {
                handled = true
                onReady()
            }

            is SessionUiState.NeedsProfile -> {
                handled = true
                onNeedsProfile()
            }

            else -> Unit
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AroundBrandMark()
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.circles_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(32.dp))

            when (uiState) {
                SessionUiState.Loading,
                is SessionUiState.Ready,
                is SessionUiState.NeedsProfile,
                -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.session_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SessionUiState.Error -> {
                    Text(
                        text = stringResource(R.string.session_error_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.session_error_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = viewModel::retry) {
                        Text(stringResource(R.string.session_retry))
                    }
                }
            }
        }
    }
}
