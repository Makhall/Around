package com.around.app.feature.places

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.around.app.core.geofence.PlaceGeofenceRegistrar
import com.around.app.core.navigation.PlaceEditorRoute
import com.around.app.data.auth.AuthRepository
import com.around.app.data.config.AppConfigRepository
import com.around.app.data.location.LocationRepository
import com.around.app.data.model.MAX_PLACE_RADIUS_METERS
import com.around.app.data.model.MIN_PLACE_RADIUS_METERS
import com.around.app.data.model.PlaceError
import com.around.app.data.model.toPlaceError
import com.around.app.data.places.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PlaceEditorUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val editing: Boolean = false,
    val name: String = "",
    val radiusMeters: Int = 150,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val initialLatitude: Double? = null,
    val initialLongitude: Double? = null,
    val mapStyleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    val error: PlaceError? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository,
    private val configRepository: AppConfigRepository,
    private val geofenceRegistrar: PlaceGeofenceRegistrar,
) : ViewModel() {

    private val route: PlaceEditorRoute =
        savedStateHandle.toRoute<PlaceEditorRoute>()
    private val circleId: String = route.circleId
    private val placeId: String? = route.placeId.takeIf { it.isNotBlank() }
    private val currentUid: String? = authRepository.currentUid

    private val _uiState = MutableStateFlow(
        PlaceEditorUiState(editing = placeId != null),
    )
    val uiState: StateFlow<PlaceEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = configRepository.load()
            _uiState.update { it.copy(mapStyleUrl = settings.mapStyleUrl) }
        }
        viewModelScope.launch {
            try {
                if (placeId != null) {
                    val place = placeRepository.getPlace(circleId, placeId)
                    if (place == null) {
                        _uiState.update {
                            it.copy(loading = false, error = PlaceError.UNKNOWN)
                        }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            editing = true,
                            name = place.name,
                            radiusMeters = place.radiusMeters,
                            latitude = place.latitude,
                            longitude = place.longitude,
                            initialLatitude = place.latitude,
                            initialLongitude = place.longitude,
                        )
                    }
                } else {
                    val locations = runCatching {
                        withTimeoutOrNull(5_000L) {
                            locationRepository.observeLocations(circleId).first()
                        }
                    }.getOrNull().orEmpty()
                    val location = locations[currentUid]
                        ?: locations.values.firstOrNull()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            initialLatitude = location?.latitude,
                            initialLongitude = location?.longitude,
                        )
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not load place", error)
                _uiState.update {
                    it.copy(loading = false, error = PlaceError.UNKNOWN)
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
    }

    fun onRadiusChange(value: Int) {
        _uiState.update {
            it.copy(
                radiusMeters = value.coerceIn(
                    MIN_PLACE_RADIUS_METERS,
                    MAX_PLACE_RADIUS_METERS,
                ),
                error = null,
            )
        }
    }

    fun onMapPicked(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                latitude = latitude,
                longitude = longitude,
                error = null,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) {
            return
        }
        if (state.name.trim().isEmpty()) {
            _uiState.update { it.copy(error = PlaceError.NAME_REQUIRED) }
            return
        }
        if (state.radiusMeters !in
            MIN_PLACE_RADIUS_METERS..MAX_PLACE_RADIUS_METERS
        ) {
            _uiState.update { it.copy(error = PlaceError.INVALID_RADIUS) }
            return
        }
        val latitude = state.latitude
        val longitude = state.longitude
        if (latitude == null || longitude == null) {
            _uiState.update { it.copy(error = PlaceError.LOCATION_REQUIRED) }
            return
        }
        val uid = currentUid ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            placeRepository.savePlace(
                circleId = circleId,
                uid = uid,
                placeId = placeId,
                name = state.name,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = state.radiusMeters,
            ).fold(
                onSuccess = {
                    geofenceRegistrar.refresh()
                    _uiState.update { it.copy(saving = false, saved = true) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error = throwable.toPlaceError(),
                        )
                    }
                },
            )
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    private companion object {
        const val TAG = "PlaceEditorViewModel"
    }
}
