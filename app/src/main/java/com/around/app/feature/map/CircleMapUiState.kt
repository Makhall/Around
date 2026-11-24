package com.around.app.feature.map

import com.around.app.data.model.Circle
import com.around.app.data.model.CircleError
import com.around.app.data.model.CircleMember
import com.around.app.data.model.MemberLocation

data class MapMember(
    val member: CircleMember,
    val location: MemberLocation?,
)

data class CircleMapUiState(
    val loading: Boolean = true,
    val circle: Circle? = null,
    val members: List<MapMember> = emptyList(),
    val currentUid: String? = null,
    val mapStyleUrl: String = DEFAULT_MAP_STYLE_URL,
    val sharingAllowed: Boolean = true,
    val sharingUpdating: Boolean = false,
    val error: CircleError? = null,
    val removedFromCircle: Boolean = false,
) {
    val currentMember: MapMember?
        get() = members.firstOrNull { it.member.uid == currentUid }

    val sharing: Boolean
        get() = currentMember?.member?.sharing == true
}

const val DEFAULT_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
