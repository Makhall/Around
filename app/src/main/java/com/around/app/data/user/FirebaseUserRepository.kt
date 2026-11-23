package com.around.app.data.user

import com.around.app.data.model.UserProfile
import com.around.app.data.model.UserSettings
import com.around.app.data.model.toAvatar
import com.around.app.data.model.toFirestoreMap
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserRepository {

    override fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUserProfile())
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getProfile(uid: String): UserProfile? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toUserProfile()
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        val userRef = firestore.collection("users").document(profile.uid)
        val existing = userRef.get().await()

        val data = mutableMapOf<String, Any>(
            "displayName" to profile.displayName,
            "avatar" to profile.avatar.toFirestoreMap(),
            "locale" to profile.locale,
            "settings" to mapOf("shareBattery" to profile.settings.shareBattery),
        )
        if (!existing.exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
        }
        userRef.set(data, SetOptions.merge()).await()

        updateMemberCopies(profile)
    }

    private suspend fun updateMemberCopies(profile: UserProfile) {
        val circles = firestore.collection("circles")
            .whereArrayContains("memberUids", profile.uid)
            .get()
            .await()

        if (circles.isEmpty) {
            return
        }

        val batch = firestore.batch()
        circles.documents.forEach { circle ->
            val memberRef = circle.reference.collection("members").document(profile.uid)
            batch.update(
                memberRef,
                mapOf(
                    "displayName" to profile.displayName,
                    "avatar" to profile.avatar.toFirestoreMap(),
                ),
            )
        }
        batch.commit().await()
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) {
            return null
        }
        val displayName = getString("displayName") ?: return null
        val settings = get("settings") as? Map<*, *>
        return UserProfile(
            uid = id,
            displayName = displayName,
            avatar = get("avatar").toAvatar(),
            locale = getString("locale") ?: "en",
            createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            settings = UserSettings(
                shareBattery = settings?.get("shareBattery") as? Boolean ?: true,
            ),
        )
    }
}
