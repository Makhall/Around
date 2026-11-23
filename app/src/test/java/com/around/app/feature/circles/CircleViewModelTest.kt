package com.around.app.feature.circles

import com.around.app.FakeCircleRepository
import com.around.app.MainDispatcherRule
import com.around.app.data.model.CircleError
import com.around.app.data.model.CircleOperationException
import com.around.app.data.model.JoinedCircle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateCircleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `rejects an empty name`() {
        val viewModel = CreateCircleViewModel(FakeCircleRepository())

        viewModel.create()

        assertEquals(CircleError.INVALID_NAME, viewModel.uiState.value.error)
    }

    @Test
    fun `creates a Circle and keeps the invite code`() {
        val repository = FakeCircleRepository()
        val viewModel = CreateCircleViewModel(repository)

        viewModel.onNameChange("Family")
        viewModel.create()

        assertEquals("Family", repository.lastCreatedName)
        assertEquals("ABCDEFGH", viewModel.uiState.value.created?.inviteCode)
    }

    @Test
    fun `surfaces a server error`() {
        val repository = FakeCircleRepository(
            createResult = Result.failure(
                CircleOperationException(CircleError.TOO_MANY_CIRCLES),
            ),
        )
        val viewModel = CreateCircleViewModel(repository)

        viewModel.onNameChange("Fourth Circle")
        viewModel.create()

        assertEquals(CircleError.TOO_MANY_CIRCLES, viewModel.uiState.value.error)
    }
}

class JoinCircleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `normalizes typed codes`() {
        val viewModel = JoinCircleViewModel(FakeCircleRepository())

        viewModel.onCodeChange("ab12-cd34")

        assertEquals("AB12CD34", viewModel.uiState.value.code)
    }

    @Test
    fun `requires eight characters`() {
        val viewModel = JoinCircleViewModel(FakeCircleRepository())

        viewModel.onCodeChange("ABC")
        viewModel.join()

        assertEquals(CircleError.CODE_REQUIRED, viewModel.uiState.value.error)
    }

    @Test
    fun `joins with a normalized code`() {
        val repository = FakeCircleRepository(
            joinResult = Result.success(JoinedCircle("circle-1", "Family")),
        )
        val viewModel = JoinCircleViewModel(repository)

        viewModel.onCodeChange("abcdefgh")
        viewModel.join()

        assertEquals("ABCDEFGH", repository.lastJoinCode)
        assertEquals("circle-1", viewModel.uiState.value.joined?.circleId)
    }

    @Test
    fun `surfaces an invite error`() {
        val repository = FakeCircleRepository(
            joinResult = Result.failure(
                CircleOperationException(CircleError.CODE_EXPIRED),
            ),
        )
        val viewModel = JoinCircleViewModel(repository)

        viewModel.onCodeChange("ABCDEFGH")
        viewModel.join()

        assertEquals(CircleError.CODE_EXPIRED, viewModel.uiState.value.error)
    }
}
