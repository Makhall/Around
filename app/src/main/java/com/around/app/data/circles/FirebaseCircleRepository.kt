package com.around.app.data.circles

import com.around.app.data.model.Circle
import com.around.app.data.model.CircleError
import com.around.app.data.model.CircleMember
import com.around.app.data.model.CircleOperationException
import com.around.app.data.model.CreatedCircle
import com.around.app.data.model.InviteCode
import com.around.app.data.model.JoinedCircle
import com.around.app.data.model.MemberRole
import com.around.app.data.model.toAvatar
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseCircleRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
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
        invoke("createCircle", mapOf("name" to name)).map { data ->
            CreatedCircle(
                circleId = data.requireString("circleId"),
                circleName = data.requireString("circleName"),
                inviteCode = data.requireString("inviteCode"),
                expiresAt = data.requireLong("expiresAt"),
            )
        }

    override suspend fun joinCircle(code: String): Result<JoinedCircle> =
        invoke("joinCircle", mapOf("code" to code)).map { data ->
            JoinedCircle(
                circleId = data.requireString("circleId"),
                circleName = data.requireString("circleName"),
            )
        }

    override suspend fun rotateInviteCode(circleId: String): Result<InviteCode> =
        invoke("rotateInviteCode", mapOf("circleId" to circleId)).map { data ->
            InviteCode(
                code = data.requireString("inviteCode"),
                expiresAt = data.requireLong("expiresAt"),
            )
        }

    override suspend fun leaveCircle(circleId: String): Result<Unit> =
        invoke("leaveCircle", mapOf("circleId" to circleId)).map { }

    override suspend fun removeMember(circleId: String, targetUid: String): Result<Unit> =
        invoke(
            "removeMember",
            mapOf("circleId" to circleId, "targetUid" to targetUid),
        ).map { }

    private suspend fun invoke(
        name: String,
        payload: Map<String, Any?>,
    ): Result<Map<*, *>> = try {
        val result = functions.getHttpsCallable(name).call(payload).await()
        val data = result.data as? Map<*, *>
            ?: throw IllegalStateException("Function $name returned no data")
        Result.success(data)
    } catch (error: FirebaseFunctionsException) {
        Result.failure(CircleOperationException(error.toCircleError()))
    } catch (error: Exception) {
        Result.failure(CircleOperationException(CircleError.NETWORK))
    }

    private fun FirebaseFunctionsException.toCircleError(): CircleError {
        val reason = (details as? Map<*, *>)?.get("reason") as? String
        return when (reason) {
            "invalid_name" -> CircleError.INVALID_NAME
            "too_many_circles" -> CircleError.TOO_MANY_CIRCLES
            "profile_missing" -> CircleError.PROFILE_MISSING
            "code_not_found" -> CircleError.CODE_NOT_FOUND
            "code_expired" -> CircleError.CODE_EXPIRED
            "code_revoked" -> CircleError.CODE_REVOKED
            "code_exhausted" -> CircleError.CODE_EXHAUSTED
            "circle_full" -> CircleError.CIRCLE_FULL
            "already_member" -> CircleError.ALREADY_MEMBER
            "rate_limited" -> CircleError.RATE_LIMITED
            "owner_cannot_leave" -> CircleError.OWNER_CANNOT_LEAVE
            "not_owner" -> CircleError.NOT_OWNER
            "member_not_found" -> CircleError.MEMBER_NOT_FOUND
            "circle_not_found" -> CircleError.CIRCLE_NOT_FOUND
            else -> when (code) {
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                FirebaseFunctionsException.Code.INTERNAL,
                -> CircleError.NETWORK

                FirebaseFunctionsException.Code.UNAUTHENTICATED -> CircleError.UNAUTHENTICATED
                else -> CircleError.UNKNOWN
            }
        }
    }

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

    private fun Map<*, *>.requireString(key: String): String =
        this[key] as? String ?: error("Missing $key")

    private fun Map<*, *>.requireLong(key: String): Long =
        (this[key] as? Number)?.toLong() ?: error("Missing $key")
}
