package com.around.app.data.history

import com.around.app.data.model.HistoryPoint
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class RealtimeHistoryRepository @Inject constructor(
    private val root: DatabaseReference,
) : HistoryRepository {

    override fun observeHistory(
        circleId: String,
        uid: String,
        since: Long,
        limit: Int,
    ): Flow<List<HistoryPoint>> = callbackFlow {
        val query = root.child("history")
            .child(circleId)
            .child(uid)
            .orderByChild("t")
            .startAt(since.toDouble())
            .limitToLast(limit)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val points = snapshot.children.mapNotNull { child ->
                    child.toHistoryPoint(uid)
                }.sortedBy { it.timestamp }
                trySend(points)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    override suspend fun appendHistory(
        circleIds: Set<String>,
        uid: String,
        point: HistoryPoint,
    ): Result<Unit> = runCatching {
        if (circleIds.isEmpty()) {
            return@runCatching
        }

        val values = buildMap<String, Any> {
            put("lat", point.latitude)
            put("lng", point.longitude)
            point.accuracyMeters?.let { put("acc", it) }
            put("t", point.timestamp)
        }
        val updates = circleIds.associate { circleId ->
            "history/$circleId/$uid/${point.timestamp}" to values
        }
        root.updateChildren(updates).await()
    }

    override suspend fun clearHistory(
        circleId: String,
        uid: String,
    ): Result<Unit> = runCatching {
        root.child("history").child(circleId).child(uid).removeValue().await()
    }

    private fun DataSnapshot.toHistoryPoint(uid: String): HistoryPoint? {
        val lat = (child("lat").value as? Number)?.toDouble() ?: return null
        val lng = (child("lng").value as? Number)?.toDouble() ?: return null
        val timestamp = (child("t").value as? Number)?.toLong() ?: return null
        return HistoryPoint(
            uid = uid,
            latitude = lat,
            longitude = lng,
            accuracyMeters = (child("acc").value as? Number)?.toFloat(),
            timestamp = timestamp,
        )
    }
}
