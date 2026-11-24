package com.around.app.feature.history

import com.around.app.data.model.Circle
import com.around.app.data.model.CircleError
import com.around.app.data.model.CircleMember
import com.around.app.data.model.HistoryPoint

data class HistoryUiState(
    val loading: Boolean = true,
    val circle: Circle? = null,
    val members: List<CircleMember> = emptyList(),
    val currentUid: String? = null,
    val selectedUid: String? = null,
    val rangeHours: Int = 24,
    val points: List<HistoryPoint> = emptyList(),
    val historyLoading: Boolean = false,
    val mapStyleUrl: String = DEFAULT_HISTORY_MAP_STYLE_URL,
    val error: CircleError? = null,
    val removedFromCircle: Boolean = false,
) {
    val selectedMember: CircleMember?
        get() = members.firstOrNull { it.uid == selectedUid }
}

const val DEFAULT_HISTORY_MAP_STYLE_URL =
    "https://tiles.openfreemap.org/styles/liberty"
