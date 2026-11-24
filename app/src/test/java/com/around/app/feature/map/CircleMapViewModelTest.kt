package com.around.app.feature.map

import androidx.lifecycle.SavedStateHandle
import com.around.app.FakeAuthRepository
import com.around.app.FakeCircleRepository
import com.around.app.FakeConfigRepository
import com.around.app.FakeLocationRepository
import com.around.app.FakePlaceRepository
import com.around.app.MainDispatcherRule
import com.around.app.data.config.RemoteSettings
import com.around.app.data.model.Avatar
import com.around.app.data.model.Circle
import com.around.app.data.model.CircleMember
import com.around.app.data.model.MemberLocation
import com.around.app.data.model.MemberRole
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CircleMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = 1_700_000_000_000L

    private fun member(
        uid: String,
        name: String,
        sharing: Boolean = true,
        role: MemberRole = MemberRole.MEMBER,
    ) = CircleMember(
        uid = uid,
        displayName = name,
        avatar = Avatar.Default,
        role = role,
        joinedAt = 1L,
        sharing = sharing,
        allowRing = true,
    )

    private fun location(uid: String, timestamp: Long = now) = MemberLocation(
        uid = uid,
        latitude = -6.2,
        longitude = 106.8,
        accuracyMeters = 8f,
        speedMps = 1.4f,
        batteryPercent = 82,
        charging = false,
        fixTime = timestamp,
        serverTime = timestamp,
    )

    private fun viewModel(
        circle: Circle? = Circle(
            id = "circle-1",
            name = "Family",
            ownerUid = "uid-alice",
            memberCount = 2,
            memberUids = listOf("uid-alice", "uid-bob"),
            createdAt = 1L,
        ),
        members: List<CircleMember> = listOf(
            member("uid-alice", "Alice", role = MemberRole.OWNER),
            member("uid-bob", "Bob"),
        ),
        locations: Map<String, MemberLocation> = mapOf(
            "uid-alice" to location("uid-alice"),
            "uid-bob" to location("uid-bob"),
        ),
        config: FakeConfigRepository = FakeConfigRepository(),
        locationRepository: FakeLocationRepository = FakeLocationRepository(locations),
        circleRepository: FakeCircleRepository = FakeCircleRepository(
            circleFlow = MutableStateFlow(circle),
            membersFlow = MutableStateFlow(members),
        ),
    ): Pair<CircleMapViewModel, Pair<FakeCircleRepository, FakeLocationRepository>> {
        val vm = CircleMapViewModel(
            savedStateHandle = SavedStateHandle(mapOf("circleId" to "circle-1")),
            authRepository = FakeAuthRepository(),
            circleRepository = circleRepository,
            locationRepository = locationRepository,
            placeRepository = FakePlaceRepository(),
            configRepository = config,
        )
        return vm to (circleRepository to locationRepository)
    }

    @Test
    fun `combines members and live locations`() {
        val (viewModel, fakes) = viewModel()
        viewModel.start()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("Family", state.circle?.name)
        assertEquals(2, state.members.size)
        assertEquals("uid-alice", state.members.first().member.uid)
        assertEquals("Bob", state.members.last().member.displayName)
        assertEquals(82, state.members.last().location?.batteryPercent)
        assertTrue(state.sharing)

        viewModel.stop()
        assertEquals(0, fakes.second.observerCount)
    }

    @Test
    fun `detaches location listeners when stopped`() {
        val (viewModel, fakes) = viewModel()
        viewModel.start()
        assertEquals(1, fakes.second.observerCount)

        viewModel.stop()
        assertEquals(0, fakes.second.observerCount)

        viewModel.start()
        assertEquals(1, fakes.second.observerCount)
        viewModel.stop()
    }

    @Test
    fun `disabling sharing writes the flag and clears the location`() {
        val (viewModel, fakes) = viewModel()
        viewModel.start()

        viewModel.setSharing(false)

        val (circleRepository, locationRepository) = fakes
        assertEquals(
            Triple("circle-1", "uid-alice", false),
            circleRepository.lastSharing,
        )
        assertEquals(setOf("circle-1") to "uid-alice", locationRepository.cleared.single())
    }

    @Test
    fun `remote config disables sharing and overrides the map style`() {
        val config = FakeConfigRepository(
            RemoteSettings(
                mapStyleUrl = "https://example.com/other.json",
                sharingEnabled = false,
            ),
        )
        val (viewModel) = viewModel(config = config)

        val state = viewModel.uiState.value
        assertFalse(state.sharingAllowed)
        assertEquals("https://example.com/other.json", state.mapStyleUrl)
        assertEquals(1, config.loadCount)
    }

    @Test
    fun `a missing Circle marks the member as removed`() {
        val (viewModel) = viewModel(circle = null)
        viewModel.start()

        assertTrue(viewModel.uiState.value.removedFromCircle)
        assertNull(viewModel.uiState.value.circle)
        viewModel.stop()
    }

    @Test
    fun `stale locations are detected`() {
        val fresh = location("uid-bob", now)
        assertFalse(fresh.isStale(now, MemberLocation.STALE_THRESHOLD_MS))
        assertTrue(
            fresh.isStale(
                now + MemberLocation.STALE_THRESHOLD_MS + 1,
                MemberLocation.STALE_THRESHOLD_MS,
            ),
        )
    }
}
