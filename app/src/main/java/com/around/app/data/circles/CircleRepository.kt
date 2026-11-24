package com.around.app.data.circles

import com.around.app.data.model.Circle
import com.around.app.data.model.CircleMember
import com.around.app.data.model.CreatedCircle
import com.around.app.data.model.InviteCode
import com.around.app.data.model.JoinedCircle
import kotlinx.coroutines.flow.Flow

interface CircleRepository {
    fun observeCircles(uid: String): Flow<List<Circle>>

    suspend fun getCircles(uid: String): List<Circle>

    fun observeCircle(circleId: String): Flow<Circle?>

    fun observeMembers(circleId: String): Flow<List<CircleMember>>

    suspend fun createCircle(name: String): Result<CreatedCircle>

    suspend fun joinCircle(code: String): Result<JoinedCircle>

    suspend fun rotateInviteCode(circleId: String): Result<InviteCode>

    suspend fun leaveCircle(circleId: String): Result<Unit>

    suspend fun removeMember(circleId: String, targetUid: String): Result<Unit>

    suspend fun setSharing(circleId: String, uid: String, sharing: Boolean): Result<Unit>

    suspend fun setHistoryEnabled(
        circleId: String,
        uid: String,
        enabled: Boolean,
    ): Result<Unit>

    suspend fun setPlaceAlerts(
        circleId: String,
        uid: String,
        enabled: Boolean,
    ): Result<Unit>
}
