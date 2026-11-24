package com.around.app.feature.circles

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.around.app.core.navigation.CircleDetailRoute
import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.config.AppConfigRepository
import com.around.app.data.history.HistoryRepository
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
class CircleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val historyRepository: HistoryRepository,
    private val configRepository: AppConfigRepository,
) : ViewModel() {

    private val circleId: String = savedStateHandle.toRoute<CircleDetailRoute>().circleId
    private val currentUid: String? = authRepository.currentUid

    private val _uiState = MutableStateFlow(
        CircleDetailUiState(currentUid = currentUid),
    )
    val uiState: StateFlow<CircleDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeCircle()
        viewModelScope.launch {
            val settings = configRepository.load()
            _uiState.update { it.copy(sharingAllowed = settings.sharingEnabled) }
        }
    }

    fun retry() {
        _uiState.update { it.copy(loading = true, error = null) }
        observeCircle()
    }

    fun generateInviteCode() {
        if (_uiState.value.generatingCode) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(generatingCode = true, error = null) }
            circleRepository.rotateInviteCode(circleId).fold(
                onSuccess = { invite ->
                    _uiState.update {
                        it.copy(generatingCode = false, inviteCode = invite)
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(generatingCode = false, error = throwable.toCircleError())
                    }
                },
            )
        }
    }

    fun removeMember(targetUid: String) {
        if (_uiState.value.removingUid != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(removingUid = targetUid, error = null) }
            circleRepository.removeMember(circleId, targetUid).fold(
                onSuccess = {
                    _uiState.update { it.copy(removingUid = null) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(removingUid = null, error = throwable.toCircleError())
                    }
                },
            )
        }
    }

    fun leaveCircle() {
        if (_uiState.value.leaving) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(leaving = true, error = null) }
            circleRepository.leaveCircle(circleId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(leaving = false, removedFromCircle = true)
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(leaving = false, error = throwable.toCircleError())
                    }
                },
            )
        }
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

    fun setPlaceAlerts(enabled: Boolean) {
        val uid = currentUid ?: return
        if (_uiState.value.placeAlertsUpdating) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(placeAlertsUpdating = true, error = null) }
            circleRepository.setPlaceAlerts(circleId, uid, enabled).fold(
                onSuccess = {
                    _uiState.update { it.copy(placeAlertsUpdating = false) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            placeAlertsUpdating = false,
                            error = throwable.toCircleError(),
                        )
                    }
                },
            )
        }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        val uid = currentUid ?: return
        if (_uiState.value.historyUpdating) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(historyUpdating = true, error = null) }
            circleRepository.setHistoryEnabled(circleId, uid, enabled).fold(
                onSuccess = {
                    if (!enabled) {
                        historyRepository.clearHistory(circleId, uid)
                    }
                    _uiState.update { it.copy(historyUpdating = false) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            historyUpdating = false,
                            error = throwable.toCircleError(),
                        )
                    }
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeCircle() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                circleRepository.observeCircle(circleId),
                circleRepository.observeMembers(circleId),
            ) { circle, members -> circle to members }
                .catch { throwable ->
                    Log.w(TAG, "Circle listeners failed", throwable)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = CircleError.NETWORK,
                        )
                    }
                }
                .collect { (circle, members) ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            circle = circle,
                            members = members,
                            currentUid = currentUid,
                            removedFromCircle = circle == null,
                        )
                    }
                }
        }
    }

    private companion object {
        const val TAG = "CircleDetailViewModel"
    }
}
