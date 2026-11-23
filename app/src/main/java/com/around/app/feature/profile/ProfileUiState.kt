package com.around.app.feature.profile

import com.around.app.data.model.Avatar

data class ProfileUiState(
    val loading: Boolean = true,
    val name: String = "",
    val avatar: Avatar = Avatar.Default,
    val saving: Boolean = false,
    val error: ProfileError? = null,
    val saved: Boolean = false,
)

enum class ProfileError {
    NAME_REQUIRED,
    LOAD_FAILED,
    SAVE_FAILED,
}
