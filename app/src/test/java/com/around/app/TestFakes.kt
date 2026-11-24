package com.around.app

import com.around.app.data.auth.AuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.config.AppConfigRepository
import com.around.app.data.config.RemoteSettings
import com.around.app.data.history.HistoryRepository
import com.around.app.data.location.LocationRepository
import com.around.app.data.model.Avatar
import com.around.app.data.model.Circle
import com.around.app.data.model.CircleMember
import com.around.app.data.model.CreatedCircle
import com.around.app.data.model.HistoryPoint
import com.around.app.data.model.InviteCode
import com.around.app.data.model.JoinedCircle
import com.around.app.data.model.LocationPayload
import com.around.app.data.model.MemberLocation
import com.around.app.data.model.Place
import com.around.app.data.model.UserProfile
import com.around.app.data.model.UserSettings
import com.around.app.data.places.PlaceRepository
import com.around.app.data.user.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    var historyResult: Result<Unit> = Result.success(Unit),
    var placeAlertsResult: Result<Unit> = Result.success(Unit),
    var circlesFlow: Flow<List<Circle>> = flowOf(emptyList()),
    var circleFlow: Flow<Circle?> = flowOf<Circle?>(null),
    var membersFlow: Flow<List<CircleMember>> = flowOf(emptyList()),
) : CircleRepository {

    var lastJoinCode: String? = null
    var lastCreatedName: String? = null

    override fun observeCircles(uid: String): Flow<List<Circle>> = circlesFlow

    override suspend fun getCircles(uid: String): List<Circle> = circlesFlow.first()

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
    var lastHistorySetting: Triple<String, String, Boolean>? = null
    var lastPlaceAlertsSetting: Triple<String, String, Boolean>? = null

    override suspend fun setHistoryEnabled(
        circleId: String,
        uid: String,
        enabled: Boolean,
    ): Result<Unit> {
        lastHistorySetting = Triple(circleId, uid, enabled)
        return historyResult
    }

    override suspend fun setPlaceAlerts(
        circleId: String,
        uid: String,
        enabled: Boolean,
    ): Result<Unit> {
        lastPlaceAlertsSetting = Triple(circleId, uid, enabled)
        return placeAlertsResult
    }
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

class FakePlaceRepository(
    places: List<Place> = emptyList(),
) : PlaceRepository {

    val placesFlow = MutableStateFlow(places)
    val saved = mutableListOf<Place>()
    val deleted = mutableListOf<String>()

    override fun observePlaces(circleId: String): Flow<List<Place>> = placesFlow

    override suspend fun getPlaces(circleId: String): List<Place> = placesFlow.value

    override suspend fun getPlace(circleId: String, placeId: String): Place? =
        placesFlow.value.firstOrNull { it.id == placeId }

    override suspend fun savePlace(
        circleId: String,
        uid: String,
        placeId: String?,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): Result<Place> {
        val place = Place(
            id = placeId ?: "place-${saved.size + 1}",
            circleId = circleId,
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            createdBy = uid,
            createdAt = System.currentTimeMillis(),
        )
        saved += place
        return Result.success(place)
    }

    override suspend fun deletePlace(circleId: String, placeId: String): Result<Unit> {
        deleted += placeId
        return Result.success(Unit)
    }
}

class FakeHistoryRepository(
    private val points: MutableStateFlow<List<HistoryPoint>> = MutableStateFlow(emptyList()),
) : HistoryRepository {

    val appended = mutableListOf<Pair<Set<String>, HistoryPoint>>()
    val cleared = mutableListOf<Pair<String, String>>()
    var observeCount = 0
        private set

    override fun observeHistory(
        circleId: String,
        uid: String,
        since: Long,
        limit: Int,
    ): Flow<List<HistoryPoint>> = points
        .onStart { observeCount += 1 }
        .onCompletion { observeCount -= 1 }

    override suspend fun appendHistory(
        circleIds: Set<String>,
        uid: String,
        point: HistoryPoint,
    ): Result<Unit> {
        appended += circleIds to point
        return Result.success(Unit)
    }

    override suspend fun clearHistory(
        circleId: String,
        uid: String,
    ): Result<Unit> {
        cleared += circleId to uid
        return Result.success(Unit)
    }

    fun setPoints(value: List<HistoryPoint>) {
        points.value = value
    }
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
