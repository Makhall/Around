package com.around.app.data.location

import com.around.app.data.model.LocationPayload
import com.around.app.data.model.MemberLocation
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class RealtimeLocationRepository @Inject constructor(
    private val root: DatabaseReference,
) : LocationRepository {

    override suspend fun writeLocation(
        circleIds: Set<String>,
        uid: String,
        payload: LocationPayload,
    ): Result<Unit> = runCatching {
        if (circleIds.isEmpty()) {
            return@runCatching
        }

        val values = buildMap<String, Any> {
            put("lat", payload.latitude)
            put("lng", payload.longitude)
            payload.accuracyMeters?.let { put("acc", it) }
            payload.speedMps?.let { put("spd", it) }
            payload.batteryPercent?.let { put("bat", it) }
            payload.charging?.let { put("chg", it) }
            put("t", payload.fixTime)
            put("ts", ServerValue.TIMESTAMP)
        }

        val updates = circleIds.associate { circleId ->
            "locations/$circleId/$uid" to values
        }
        root.updateChildren(updates).await()
    }

    override suspend fun clearLocations(
        circleIds: Set<String>,
        uid: String,
    ): Result<Unit> = runCatching {
        if (circleIds.isEmpty()) {
            return@runCatching
        }
        val updates = circleIds.associate { circleId ->
            "locations/$circleId/$uid" to null
        }
        root.updateChildren(updates).await()
    }

    override fun observeLocations(circleId: String): Flow<Map<String, MemberLocation>> =
        callbackFlow {
            val reference = root.child("locations").child(circleId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val locations = snapshot.children.mapNotNull { child ->
                        val value = child.value as? Map<*, *> ?: return@mapNotNull null
                        val uid = child.key ?: return@mapNotNull null
                        val lat = (value["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                        val lng = (value["lng"] as? Number)?.toDouble() ?: return@mapNotNull null

                        MemberLocation(
                            uid = uid,
                            latitude = lat,
                            longitude = lng,
                            accuracyMeters = (value["acc"] as? Number)?.toFloat(),
                            speedMps = (value["spd"] as? Number)?.toFloat(),
                            batteryPercent = (value["bat"] as? Number)?.toInt(),
                            charging = value["chg"] as? Boolean,
                            fixTime = (value["t"] as? Number)?.toLong() ?: 0L,
                            serverTime = (value["ts"] as? Number)?.toLong() ?: 0L,
                        )
                    }.associateBy { it.uid }

                    trySend(locations)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }

            reference.addValueEventListener(listener)
            awaitClose { reference.removeEventListener(listener) }
        }
}
