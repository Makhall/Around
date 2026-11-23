package com.around.app.feature.circles

import com.around.app.data.model.CircleError
import com.around.app.data.model.CreatedCircle

data class CreateCircleUiState(
    val name: String = "",
    val submitting: Boolean = false,
    val error: CircleError? = null,
    val created: CreatedCircle? = null,
)
