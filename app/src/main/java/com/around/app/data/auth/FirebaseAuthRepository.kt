package com.around.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    override val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    override suspend fun signInAnonymously(): Result<String> = runCatching {
        val user = firebaseAuth.currentUser
            ?: firebaseAuth.signInAnonymously().await().user
            ?: error("Firebase returned no user after anonymous sign-in")

        user.uid
    }
}
