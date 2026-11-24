package com.around.app

import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.config.AppConfigRepository
import com.around.app.data.config.RemoteSettings
import com.around.app.data.location.LocationRepository
import com.around.app.data.model.Avatar
import com.around.app.data.model.Circle
import com.around.app.data.model.CircleMember
import com.around.app.data.model.CreatedCircle
import com.around.app.data.model.InviteCode
import com.around.app.data.model.JoinedCircle
import com.around.app.data.model.LocationPayload
import com.around.app.data.model.MemberLocation
import com.around.app.data.model.UserProfile
import com.around.app.data.model.UserSettings
import com.around.app.data.user.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

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
    var sharingResult: Result<Unit> = Result.success(Unit),
    var circlesFlow: Flow<List<Circle>> = flowOf(emptyList()),
    var circleFlow: Flow<Circle?> = flowOf<Circle?>(null),
    var membersFlow: Flow<List<CircleMember>> = flowOf(emptyList()),
) : CircleRepository {

    var lastJoinCode: String? = null
    var lastCreatedName: String? = null

    override fun observeCircles(uid: String): Flow<List<Circle>> = circlesFlow

    override fun observeCircle(circleId: String): Flow<Circle?> = circleFlow

    override fun observeMembers(circleId: String): Flow<List<CircleMember>> = membersFlow

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

    override suspend fun setSharing(
        circleId: String,
        uid: String,
        sharing: Boolean,
    ): Result<Unit> {
        lastSharing = Triple(circleId, uid, sharing)
        return sharingResult
    }

    var lastSharing: Triple<String, String, Boolean>? = null
}

class FakeLocationRepository(
    locations: Map<String, MemberLocation> = emptyMap(),
) : LocationRepository {

    val locations = MutableStateFlow(locations)
    val writes = mutableListOf<Triple<Set<String>, String, LocationPayload>>()
    val cleared = mutableListOf<Pair<Set<String>, String>>()
    var observerCount = 0
        private set

    override suspend fun writeLocation(
        circleIds: Set<String>,
        uid: String,
        payload: LocationPayload,
    ): Result<Unit> {
        writes += Triple(circleIds, uid, payload)
        return Result.success(Unit)
    }

    override suspend fun clearLocations(
        circleIds: Set<String>,
        uid: String,
    ): Result<Unit> {
        cleared += circleIds to uid
        return Result.success(Unit)
    }

    override fun observeLocations(circleId: String): Flow<Map<String, MemberLocation>> =
        locations
            .onStart { observerCount += 1 }
            .onCompletion { observerCount -= 1 }
}

class FakeConfigRepository(
    var settings: RemoteSettings = RemoteSettings(
        mapStyleUrl = "https://example.com/style.json",
        sharingEnabled = true,
    ),
) : AppConfigRepository {

    var loadCount = 0
        private set

    override suspend fun load(): RemoteSettings {
        loadCount += 1
        return settings
    }
}
