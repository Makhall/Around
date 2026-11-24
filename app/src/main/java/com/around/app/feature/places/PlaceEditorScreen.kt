package com.around.app.feature.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.around.app.R
import com.around.app.data.model.MAX_PLACE_RADIUS_METERS
import com.around.app.data.model.MIN_PLACE_RADIUS_METERS
import com.around.app.data.model.PlaceError
import kotlin.math.roundToInt

@Composable
fun PlaceEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PlaceEditorViewModel = hiltViewModel(),
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
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.editing) {
                                R.string.place_editor_edit_title
                            } else {
                                R.string.place_editor_new_title
                            },
                        ),
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (uiState.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.place_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.place_name_label)) },
                singleLine = true,
                isError = uiState.error == PlaceError.NAME_REQUIRED,
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.place_radius_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.place_radius_value,
                        uiState.radiusMeters,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = uiState.radiusMeters.toFloat(),
                onValueChange = { value ->
                    val rounded = (value / 50f).roundToInt() * 50
                    viewModel.onRadiusChange(rounded)
                },
                valueRange = MIN_PLACE_RADIUS_METERS.toFloat()..
                    MAX_PLACE_RADIUS_METERS.toFloat(),
                steps = 17,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                PlacePickerMap(
                    styleUrl = uiState.mapStyleUrl,
                    latitude = uiState.latitude,
                    longitude = uiState.longitude,
                    radiusMeters = uiState.radiusMeters,
                    onLocationPicked = viewModel::onMapPicked,
                    modifier = Modifier.fillMaxSize(),
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = stringResource(R.string.place_pick_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            uiState.error?.let { error ->
                Text(
                    text = placeErrorText(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
            }

            Button(
                onClick = viewModel::save,
                enabled = !uiState.saving && uiState.latitude != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (uiState.saving) R.string.place_saving else R.string.place_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun placeErrorText(error: PlaceError): String = when (error) {
    PlaceError.NAME_REQUIRED -> stringResource(R.string.place_error_name)
    PlaceError.INVALID_RADIUS -> stringResource(R.string.place_error_radius)
    PlaceError.LOCATION_REQUIRED -> stringResource(R.string.place_error_location)
    PlaceError.TOO_MANY_PLACES -> stringResource(R.string.place_error_too_many)
    PlaceError.NETWORK -> stringResource(R.string.places_error)
    PlaceError.UNKNOWN -> stringResource(R.string.place_error_save)
}
