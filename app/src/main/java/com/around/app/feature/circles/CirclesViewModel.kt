package com.around.app.feature.circles

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CirclesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val circleRepository: CircleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CirclesUiState())
    val uiState: StateFlow<CirclesUiState> = _uiState.asStateFlow()

    private val uid: String? = authRepository.currentUid
    private var profileJob: Job? = null
    private var circlesJob: Job? = null

    init {
        start()
    }

    fun retry() {
        start()
    }

    private fun start() {
        val currentUid = uid
        if (currentUid == null) {
            _uiState.value = CirclesUiState(loading = false, error = true)
            return
        }

        profileJob?.cancel()
        circlesJob?.cancel()
        _uiState.value = CirclesUiState(loading = true)

        profileJob = viewModelScope.launch {
            userRepository.observeProfile(currentUid)
                .catch { throwable ->
                    Log.w(TAG, "Profile listener failed", throwable)
                    emit(null)
                }
                .collect { profile ->
                    _uiState.update { it.copy(profile = profile) }
                }
        }

        circlesJob = viewModelScope.launch {
            circleRepository.observeCircles(currentUid)
                .catch { throwable ->
                    Log.w(TAG, "Circles listener failed", throwable)
                    _uiState.update { it.copy(loading = false, error = true) }
                }
                .collect { circles ->
                    _uiState.update {
                        it.copy(loading = false, circles = circles, error = false)
                    }
                }
        }
    }

    private companion object {
        const val TAG = "CirclesViewModel"
    }
}
