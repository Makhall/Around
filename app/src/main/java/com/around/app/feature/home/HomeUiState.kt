package com.around.app.feature.home

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(val uid: String) : HomeUiState

    data object Error : HomeUiState
}
