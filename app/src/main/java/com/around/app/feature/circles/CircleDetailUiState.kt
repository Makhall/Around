package com.around.app.feature.circles

import com.around.app.data.model.Circle
import com.around.app.data.model.CircleError
import com.around.app.data.model.CircleMember
import com.around.app.data.model.InviteCode

data class CircleDetailUiState(
    val loading: Boolean = true,
    val circle: Circle? = null,
    val members: List<CircleMember> = emptyList(),
    val currentUid: String? = null,
    val inviteCode: InviteCode? = null,
    val generatingCode: Boolean = false,
    val leaving: Boolean = false,
    val removingUid: String? = null,
    val sharingUpdating: Boolean = false,
    val sharingAllowed: Boolean = true,
    val error: CircleError? = null,
    val removedFromCircle: Boolean = false,
)
