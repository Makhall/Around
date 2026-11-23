package com.around.app.feature.circles

import com.around.app.data.model.CircleError
import com.around.app.data.model.JoinedCircle

data class JoinCircleUiState(
    val code: String = "",
    val joining: Boolean = false,
    val error: CircleError? = null,
    val joined: JoinedCircle? = null,
)
