package com.around.app.data.user

import com.around.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeProfile(uid: String): Flow<UserProfile?>

    suspend fun getProfile(uid: String): UserProfile?

    suspend fun saveProfile(profile: UserProfile): Result<Unit>
}
