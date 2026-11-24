package com.around.app.data.location

import com.around.app.data.model.LocationPayload
import com.around.app.data.model.MemberLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    suspend fun writeLocation(
        circleIds: Set<String>,
        uid: String,
        payload: LocationPayload,
    ): Result<Unit>

    suspend fun clearLocations(circleIds: Set<String>, uid: String): Result<Unit>

    fun observeLocations(circleId: String): Flow<Map<String, MemberLocation>>
}
