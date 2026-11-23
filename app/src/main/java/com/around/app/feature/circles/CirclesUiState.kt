package com.around.app.feature.circles

import com.around.app.data.model.Circle
import com.around.app.data.model.UserProfile

data class CirclesUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val circles: List<Circle> = emptyList(),
    val error: Boolean = false,
)
