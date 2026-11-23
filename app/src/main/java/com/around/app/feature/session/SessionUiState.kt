package com.around.app.feature.session

sealed interface SessionUiState {
    data object Loading : SessionUiState

    data class NeedsProfile(val uid: String) : SessionUiState

    data object Ready : SessionUiState

    data object Error : SessionUiState
}
