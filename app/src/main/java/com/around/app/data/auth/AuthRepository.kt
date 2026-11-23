package com.around.app.data.auth

interface AuthRepository {
    val currentUid: String?

    suspend fun signInAnonymously(): Result<String>
}
