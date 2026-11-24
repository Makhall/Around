package com.around.app.feature.history

import androidx.lifecycle.SavedStateHandle
import com.around.app.FakeAuthRepository
import com.around.app.FakeCircleRepository
import com.around.app.FakeConfigRepository
import com.around.app.FakeHistoryRepository
import com.around.app.MainDispatcherRule
import com.around.app.data.model.Avatar
import com.around.app.data.model.Circle
import com.around.app.data.model.CircleMember
import com.around.app.data.model.HistoryPoint
import com.around.app.data.model.MemberRole
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()

    private fun member(uid: String, name: String) = CircleMember(
        uid = uid,
        displayName = name,
        avatar = Avatar.Default,
        role = if (uid == "uid-alice") MemberRole.OWNER else MemberRole.MEMBER,
        joinedAt = 1L,
        sharing = true,
        allowRing = true,
    )

    private fun point(uid: String, timestamp: Long) = HistoryPoint(
        uid = uid,
        latitude = -6.1754,
        longitude = 106.8272,
        accuracyMeters = 8f,
        timestamp = timestamp,
    )

    private fun viewModel(
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
    ): Pair<HistoryViewModel, FakeHistoryRepository> {
        val circle = Circle(
            id = "circle-1",
            name = "Family",
            ownerUid = "uid-alice",
            memberCount = 2,
            memberUids = listOf("uid-alice", "uid-bob"),
            createdAt = 1L,
        )
        val circleRepository = FakeCircleRepository(
            circleFlow = MutableStateFlow(circle),
            membersFlow = MutableStateFlow(
                listOf(member("uid-alice", "Alice"), member("uid-bob", "Bob")),
            ),
        )
        val viewModel = HistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("circleId" to "circle-1")),
            authRepository = FakeAuthRepository(),
            circleRepository = circleRepository,
            historyRepository = historyRepository,
            configRepository = FakeConfigRepository(),
        )
        return viewModel to historyRepository
    }

    @Test
    fun `loads members and the selected member history`() {
        val repository = FakeHistoryRepository()
        repository.setPoints(listOf(point("uid-alice", now - 1_000L)))
        val (viewModel, _) = viewModel(repository)

        viewModel.start()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("Family", state.circle?.name)
        assertEquals(2, state.members.size)
        assertEquals("uid-alice", state.selectedUid)
        assertEquals(1, state.points.size)
        assertEquals(1, repository.observeCount)

        viewModel.stop()
        assertEquals(0, repository.observeCount)
    }

    @Test
    fun `selecting a member and range restarts the history listener`() {
        val (viewModel, repository) = viewModel()
        viewModel.start()

        viewModel.selectMember("uid-bob")
        viewModel.selectRange(1)

        assertEquals("uid-bob", viewModel.uiState.value.selectedUid)
        assertEquals(1, viewModel.uiState.value.rangeHours)
        assertTrue(viewModel.uiState.value.historyLoading || repository.observeCount > 0)

        viewModel.stop()
    }

    @Test
    fun `a missing Circle is marked as removed`() {
        val repository = FakeHistoryRepository()
        val circleRepository = FakeCircleRepository(
            circleFlow = MutableStateFlow<Circle?>(null),
            membersFlow = MutableStateFlow(emptyList<CircleMember>()),
        )
        val viewModel = HistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("circleId" to "circle-1")),
            authRepository = FakeAuthRepository(),
            circleRepository = circleRepository,
            historyRepository = repository,
            configRepository = FakeConfigRepository(),
        )

        viewModel.start()

        assertTrue(viewModel.uiState.value.removedFromCircle)
        viewModel.stop()
    }
}
