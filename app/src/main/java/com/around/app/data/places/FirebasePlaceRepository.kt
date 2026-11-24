package com.around.app.data.places

import com.around.app.data.model.MAX_PLACES_PER_CIRCLE
import com.around.app.data.model.MAX_PLACE_RADIUS_METERS
import com.around.app.data.model.MIN_PLACE_RADIUS_METERS
import com.around.app.data.model.Place
import com.around.app.data.model.PlaceError
import com.around.app.data.model.PlaceOperationException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebasePlaceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PlaceRepository {

    override fun observePlaces(circleId: String): Flow<List<Place>> = callbackFlow {
        val listener = firestore.collection("circles")
            .document(circleId)
            .collection("places")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents?.mapNotNull { it.toPlace(circleId) }
                        ?.sortedWith(compareBy({ it.name.lowercase() }, { it.createdAt }))
                        .orEmpty(),
                )
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getPlace(circleId: String, placeId: String): Place? =
        firestore.collection("circles")
            .document(circleId)
            .collection("places")
            .document(placeId)
            .get()
            .await()
            .toPlace(circleId)

    override suspend fun getPlaces(circleId: String): List<Place> =
        firestore.collection("circles")
            .document(circleId)
            .collection("places")
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { it.toPlace(circleId) }

    override suspend fun savePlace(
        circleId: String,
        uid: String,
        placeId: String?,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): Result<Place> = runCatching {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw PlaceOperationException(PlaceError.NAME_REQUIRED)
        }
        if (radiusMeters !in MIN_PLACE_RADIUS_METERS..MAX_PLACE_RADIUS_METERS) {
            throw PlaceOperationException(PlaceError.INVALID_RADIUS)
        }

        val collection = firestore.collection("circles")
            .document(circleId)
            .collection("places")

        val document = if (placeId == null) {
            if (collection.limit(MAX_PLACES_PER_CIRCLE.toLong()).get().await().size() >=
                MAX_PLACES_PER_CIRCLE
            ) {
                throw PlaceOperationException(PlaceError.TOO_MANY_PLACES)
            }
            collection.document()
        } else {
            collection.document(placeId)
        }

        if (placeId == null) {
            document.set(
                mapOf(
                    "name" to trimmed,
                    "lat" to latitude,
                    "lng" to longitude,
                    "radiusM" to radiusMeters,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            document.update(
                mapOf(
                    "name" to trimmed,
                    "lat" to latitude,
                    "lng" to longitude,
                    "radiusM" to radiusMeters,
                ),
            ).await()
        }

        Place(
            id = document.id,
            circleId = circleId,
            name = trimmed,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            createdBy = uid,
            createdAt = System.currentTimeMillis(),
        )
    }

    override suspend fun deletePlace(circleId: String, placeId: String): Result<Unit> =
        runCatching {
            firestore.collection("circles")
                .document(circleId)
                .collection("places")
                .document(placeId)
                .delete()
                .await()
        }

    private fun DocumentSnapshot.toPlace(circleId: String): Place? {
        if (!exists()) {
            return null
        }
        val name = getString("name") ?: return null
        val latitude = getDouble("lat") ?: return null
        val longitude = getDouble("lng") ?: return null
        val radius = getLong("radiusM")?.toInt() ?: MIN_PLACE_RADIUS_METERS
        return Place(
            id = id,
            circleId = circleId,
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radius,
            createdBy = getString("createdBy").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        )
    }
}
