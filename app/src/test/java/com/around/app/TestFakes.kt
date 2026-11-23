package com.around.app

import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.model.Avatar
import com.around.app.data.model.Circle
import com.around.app.data.model.CircleMember
import com.around.app.data.model.CreatedCircle
import com.around.app.data.model.InviteCode
import com.around.app.data.model.JoinedCircle
import com.around.app.data.model.UserProfile
import com.around.app.data.model.UserSettings
import com.around.app.data.user.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

val testProfile = UserProfile(
    uid = "uid-alice",
    displayName = "Alice",
    avatar = Avatar.Default,
    locale = "en",
    createdAt = 1L,
    settings = UserSettings(),
)

class FakeAuthRepository(
    private val result: Result<String> = Result.success("uid-alice"),
    override val currentUid: String? = "uid-alice",
) : AuthRepository {
    override suspend fun signInAnonymously(): Result<String> = result
}

class FakeUserRepository(
    var profile: UserProfile? = testProfile,
    private val loadFailure: Throwable? = null,
) : UserRepository {

    val savedProfiles = mutableListOf<UserProfile>()

    override fun observeProfile(uid: String): Flow<UserProfile?> = flowOf(profile)

    override suspend fun getProfile(uid: String): UserProfile? {
        loadFailure?.let { throw it }
        return profile
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        this.profile = profile
        savedProfiles += profile
        return Result.success(Unit)
    }
}

class FakeCircleRepository(
    var createResult: Result<CreatedCircle> = Result.success(
        CreatedCircle(
            circleId = "circle-1",
            circleName = "Family",
            inviteCode = "ABCDEFGH",
            expiresAt = 2L,
        ),
    ),
    var joinResult: Result<JoinedCircle> = Result.success(
        JoinedCircle(circleId = "circle-1", circleName = "Family"),
    ),
    var rotateResult: Result<InviteCode> = Result.success(
        InviteCode(code = "ABCDEFGH", expiresAt = 2L),
    ),
    var leaveResult: Result<Unit> = Result.success(Unit),
    var removeResult: Result<Unit> = Result.success(Unit),
) : CircleRepository {

    var lastJoinCode: String? = null
    var lastCreatedName: String? = null

    override fun observeCircles(uid: String): Flow<List<Circle>> = flowOf(emptyList())

    override fun observeCircle(circleId: String): Flow<Circle?> = flowOf<Circle?>(null)

    override fun observeMembers(circleId: String): Flow<List<CircleMember>> = flowOf(emptyList())

    override suspend fun createCircle(name: String): Result<CreatedCircle> {
        lastCreatedName = name
        return createResult
    }

    override suspend fun joinCircle(code: String): Result<JoinedCircle> {
        lastJoinCode = code
        return joinResult
    }

    override suspend fun rotateInviteCode(circleId: String): Result<InviteCode> =
        rotateResult

    override suspend fun leaveCircle(circleId: String): Result<Unit> = leaveResult

    override suspend fun removeMember(circleId: String, targetUid: String): Result<Unit> =
        removeResult
}
