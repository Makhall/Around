package com.around.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.around.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        signIn()
    }

    fun retry() {
        signIn()
    }

    private fun signIn() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            _uiState.value = authRepository.signInAnonymously().fold(
                onSuccess = { uid -> HomeUiState.Ready(uid) },
                onFailure = { HomeUiState.Error },
            )
        }
    }
}
