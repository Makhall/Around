package com.around.app.data.places

import com.around.app.data.remote.AroundApi
import javax.inject.Inject
import javax.inject.Singleton

interface PlaceEventRepository {
    suspend fun sendEvent(circleId: String, placeId: String, type: String): Result<Unit>
}

@Singleton
class ApiPlaceEventRepository @Inject constructor(
    private val api: AroundApi,
) : PlaceEventRepository {
    override suspend fun sendEvent(
        circleId: String,
        placeId: String,
        type: String,
    ): Result<Unit> = api.sendPlaceEvent(circleId, placeId, type)
}
