package com.around.app.data.places

import com.around.app.data.model.Place
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun observePlaces(circleId: String): Flow<List<Place>>

    suspend fun getPlaces(circleId: String): List<Place>

    suspend fun getPlace(circleId: String, placeId: String): Place?

    suspend fun savePlace(
        circleId: String,
        uid: String,
        placeId: String?,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): Result<Place>

    suspend fun deletePlace(circleId: String, placeId: String): Result<Unit>
}
