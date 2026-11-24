package com.around.app.feature.places

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.around.app.core.geofence.PlaceGeofenceRegistrar
import com.around.app.core.navigation.PlacesRoute
import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.model.PlaceError
import com.around.app.data.model.toPlaceError
import com.around.app.data.places.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlacesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val placeRepository: PlaceRepository,
    private val geofenceRegistrar: PlaceGeofenceRegistrar,
) : ViewModel() {

    private val circleId: String = savedStateHandle.get<String>("circleId")
        ?: savedStateHandle.toRoute<PlacesRoute>().circleId
    private val currentUid: String? = authRepository.currentUid

    private val _uiState = MutableStateFlow(PlacesUiState(currentUid = currentUid))
    val uiState: StateFlow<PlacesUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun start() {
        if (observeJob?.isActive == true) {
            return
        }
        observeJob = viewModelScope.launch {
            combine(
                circleRepository.observeCircle(circleId),
                placeRepository.observePlaces(circleId),
            ) { circle, places -> circle to places }
                .catch { throwable ->
                    Log.w(TAG, "Places listener failed", throwable)
                    _uiState.update {
                        it.copy(loading = false, error = PlaceError.NETWORK)
                    }
                }
                .collect { (circle, places) ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            circle = circle,
                            places = places,
                        )
                    }
                }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }

    fun retry() {
        stop()
        _uiState.update { it.copy(loading = true, error = null) }
        start()
    }

    fun deletePlace(placeId: String) {
        if (_uiState.value.deletingId != null) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = placeId, error = null) }
            placeRepository.deletePlace(circleId, placeId).fold(
                onSuccess = {
                    _uiState.update { it.copy(deletingId = null) }
                    geofenceRegistrar.refresh()
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            deletingId = null,
                            error = throwable.toPlaceError(),
                        )
                    }
                },
            )
        }
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val TAG = "PlacesViewModel"
    }
}
