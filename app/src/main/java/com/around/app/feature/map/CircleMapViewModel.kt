package com.around.app.feature.map

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.around.app.core.navigation.CircleMapRoute
import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.config.AppConfigRepository
import com.around.app.data.location.LocationRepository
import com.around.app.data.model.CircleError
import com.around.app.data.model.toCircleError
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
class CircleMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val configRepository: AppConfigRepository,
) : ViewModel() {

    private val circleId: String = savedStateHandle.get<String>("circleId")
        ?: savedStateHandle.toRoute<CircleMapRoute>().circleId
    private val currentUid: String? = authRepository.currentUid

    private val _uiState = MutableStateFlow(
        CircleMapUiState(currentUid = currentUid),
    )
    val uiState: StateFlow<CircleMapUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = configRepository.load()
            _uiState.update {
                it.copy(
                    mapStyleUrl = settings.mapStyleUrl,
                    sharingAllowed = settings.sharingEnabled,
                )
            }
        }
    }

    /** Attaches the Firestore and RTDB listeners. Called while the map is visible. */
    fun start() {
        if (observeJob?.isActive == true) {
            return
        }

        observeJob = viewModelScope.launch {
            combine(
                circleRepository.observeCircle(circleId),
                circleRepository.observeMembers(circleId),
                locationRepository.observeLocations(circleId),
            ) { circle, members, locations ->
                Triple(circle, members, locations)
            }
                .catch { throwable ->
                    Log.w(TAG, "Map listeners failed", throwable)
                    _uiState.update {
                        it.copy(loading = false, error = CircleError.NETWORK)
                    }
                }
                .collect { (circle, members, locations) ->
                    val mapped = members
                        .map { member ->
                            MapMember(member = member, location = locations[member.uid])
                        }
                        .sortedWith(
                            compareBy(
                                { it.member.uid != currentUid },
                                { it.member.displayName.lowercase() },
                            ),
                        )
                    _uiState.update {
                        it.copy(
                            loading = false,
                            circle = circle,
                            members = mapped,
                            currentUid = currentUid,
                            removedFromCircle = circle == null,
                        )
                    }
                }
        }
    }

    /** Detaches all listeners when the map leaves the foreground. */
    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }

    fun retry() {
        stop()
        _uiState.update { it.copy(loading = true, error = null) }
        start()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setSharing(enabled: Boolean) {
        val uid = currentUid ?: return
        if (_uiState.value.sharingUpdating) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(sharingUpdating = true, error = null) }
            circleRepository.setSharing(circleId, uid, enabled).fold(
                onSuccess = {
                    if (!enabled) {
                        locationRepository.clearLocations(setOf(circleId), uid)
                    }
                    _uiState.update { it.copy(sharingUpdating = false) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            sharingUpdating = false,
                            error = throwable.toCircleError(),
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
        const val TAG = "CircleMapViewModel"
    }
}
