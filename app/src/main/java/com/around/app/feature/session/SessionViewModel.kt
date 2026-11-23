package com.around.app.feature.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.around.app.data.auth.AuthRepository
import com.around.app.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        start()
    }

    fun retry() {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            _uiState.value = SessionUiState.Loading
            authRepository.signInAnonymously().fold(
                onSuccess = { uid ->
                    userRepository.observeProfile(uid)
                        .catch { throwable ->
                            Log.w(TAG, "Profile lookup failed", throwable)
                            _uiState.value = SessionUiState.Error
                        }
                        .collect { profile ->
                            _uiState.value = if (profile == null) {
                                SessionUiState.NeedsProfile(uid)
                            } else {
                                SessionUiState.Ready
                            }
                        }
                },
                onFailure = { throwable ->
                    Log.w(TAG, "Anonymous sign-in failed", throwable)
                    _uiState.value = SessionUiState.Error
                },
            )
        }
    }

    private companion object {
        const val TAG = "SessionViewModel"
    }
}
