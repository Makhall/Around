package com.around.app.feature.history

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.around.app.core.navigation.HistoryRoute
import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.config.AppConfigRepository
import com.around.app.data.history.HistoryRepository
import com.around.app.data.model.CircleError
import com.around.app.data.model.HistoryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class HistorySelection(
    val uid: String?,
    val rangeHours: Int,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val historyRepository: HistoryRepository,
    private val configRepository: AppConfigRepository,
) : ViewModel() {

    private val circleId: String = savedStateHandle.get<String>("circleId")
        ?: savedStateHandle.toRoute<HistoryRoute>().circleId
    private val currentUid: String? = authRepository.currentUid

    private val selection = MutableStateFlow(
        HistorySelection(uid = currentUid, rangeHours = 24),
    )

    private val _uiState = MutableStateFlow(
        HistoryUiState(currentUid = currentUid, selectedUid = currentUid),
    )
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var historyJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = configRepository.load()
            _uiState.update { it.copy(mapStyleUrl = settings.mapStyleUrl) }
        }
    }

    fun start() {
        if (observeJob?.isActive == true) {
            return
        }

        observeJob = viewModelScope.launch {
            combine(
                circleRepository.observeCircle(circleId),
                circleRepository.observeMembers(circleId),
            ) { circle, members -> circle to members }
                .catch { throwable ->
                    Log.w(TAG, "History member listeners failed", throwable)
                    _uiState.update {
                        it.copy(loading = false, error = CircleError.NETWORK)
                    }
                }
                .collect { (circle, members) ->
                    val sorted = members.sortedWith(
                        compareBy(
                            { it.uid != currentUid },
                            { it.displayName.lowercase() },
                        ),
                    )
                    val selected = selection.value.uid
                        ?.takeIf { uid -> members.any { it.uid == uid } }
                        ?: members.firstOrNull { it.uid == currentUid }?.uid
                        ?: members.firstOrNull()?.uid
                    if (selected != selection.value.uid) {
                        selection.update { it.copy(uid = selected) }
                    }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            circle = circle,
                            members = sorted,
                            selectedUid = selected,
                            removedFromCircle = circle == null,
                        )
                    }
                }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        historyJob = viewModelScope.launch {
            selection
                .flatMapLatest { selected ->
                    val uid = selected.uid
                    if (uid == null) {
                        _uiState.update {
                            it.copy(points = emptyList(), historyLoading = false)
                        }
                        flowOf(emptyList<HistoryPoint>())
                    } else {
                        _uiState.update { it.copy(historyLoading = true) }
                        val since = System.currentTimeMillis() -
                            selected.rangeHours * 60L * 60L * 1000L
                        historyRepository.observeHistory(circleId, uid, since)
                    }
                }
                .catch { throwable ->
                    Log.w(TAG, "History listener failed", throwable)
                    _uiState.update {
                        it.copy(
                            historyLoading = false,
                            points = emptyList(),
                            error = CircleError.NETWORK,
                        )
                    }
                }
                .collect { points ->
                    _uiState.update {
                        it.copy(
                            historyLoading = false,
                            points = points,
                        )
                    }
                }
        }
    }

    fun stop() {
        observeJob?.cancel()
        historyJob?.cancel()
        observeJob = null
        historyJob = null
    }

    fun retry() {
        stop()
        _uiState.update { it.copy(loading = true, error = null) }
        start()
    }

    fun selectMember(uid: String) {
        selection.update { it.copy(uid = uid) }
        _uiState.update { it.copy(selectedUid = uid, points = emptyList()) }
    }

    fun selectRange(hours: Int) {
        selection.update { it.copy(rangeHours = hours) }
        _uiState.update {
            it.copy(rangeHours = hours, points = emptyList(), historyLoading = true)
        }
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val TAG = "HistoryViewModel"
    }
}
