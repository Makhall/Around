package com.around.app.data.circles

import com.around.app.data.model.Circle
import com.around.app.data.model.CircleMember
import com.around.app.data.model.CreatedCircle
import com.around.app.data.model.InviteCode
import com.around.app.data.model.JoinedCircle
import com.around.app.data.model.MemberRole
import com.around.app.data.model.toAvatar
import com.around.app.data.remote.AroundApi
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch

@Singleton
class FirebaseCircleRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val api: AroundApi,
) : CircleRepository {

    override fun observeCircles(uid: String): Flow<List<Circle>> = callbackFlow {
        val listener = firestore.collection("circles")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toCircle() }.orEmpty())
            }

        awaitClose { listener.remove() }
    }.catch { throwable ->
        if (throwable is FirebaseFirestoreException &&
            throwable.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            emit(emptyList())
        } else {
            throw throwable
        }
    }

    override fun observeCircle(circleId: String): Flow<Circle?> = callbackFlow {
        val listener = firestore.collection("circles")
            .document(circleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toCircle())
            }

        awaitClose { listener.remove() }
    }.catch { throwable ->
        if (throwable is FirebaseFirestoreException &&
            throwable.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            emit(null)
        } else {
            throw throwable
        }
    }

    override fun observeMembers(circleId: String): Flow<List<CircleMember>> = callbackFlow {
        val listener = firestore.collection("circles")
            .document(circleId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toMember() }.orEmpty())
            }

        awaitClose { listener.remove() }
    }.catch { throwable ->
        if (throwable is FirebaseFirestoreException &&
            throwable.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            emit(emptyList())
        } else {
            throw throwable
        }
    }

    override suspend fun createCircle(name: String): Result<CreatedCircle> =
        api.createCircle(name)

    override suspend fun joinCircle(code: String): Result<JoinedCircle> =
        api.joinCircle(code)

    override suspend fun rotateInviteCode(circleId: String): Result<InviteCode> =
        api.rotateInviteCode(circleId)

    override suspend fun leaveCircle(circleId: String): Result<Unit> =
        api.leaveCircle(circleId)

    override suspend fun removeMember(circleId: String, targetUid: String): Result<Unit> =
        api.removeMember(circleId, targetUid)

    private fun DocumentSnapshot.toCircle(): Circle? {
        if (!exists()) {
            return null
        }
        val name = getString("name") ?: return null
        val ownerUid = getString("ownerUid") ?: return null
        val memberUids = (get("memberUids") as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
        return Circle(
            id = id,
            name = name,
            ownerUid = ownerUid,
            memberCount = (getLong("memberCount") ?: memberUids.size.toLong()).toInt(),
            memberUids = memberUids,
            createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        )
    }

    private fun DocumentSnapshot.toMember(): CircleMember? {
        if (!exists()) {
            return null
        }
        return CircleMember(
            uid = id,
            displayName = getString("displayName") ?: "Around user",
            avatar = get("avatar").toAvatar(),
            role = MemberRole.fromWire(getString("role")),
            joinedAt = getTimestamp("joinedAt")?.toDate()?.time ?: 0L,
            sharing = getBoolean("sharing") ?: false,
            allowRing = getBoolean("allowRing") ?: true,
        )
    }
}
