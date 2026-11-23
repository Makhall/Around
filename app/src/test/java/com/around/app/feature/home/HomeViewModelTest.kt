package com.around.app.feature.home

import com.around.app.MainDispatcherRule
import com.around.app.data.auth.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `signs in anonymously and exposes ready state`() {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(Result.success("uid-123")),
        )

        assertEquals(HomeUiState.Ready("uid-123"), viewModel.uiState.value)
    }

    @Test
    fun `exposes error when sign-in fails`() {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(Result.failure(IllegalStateException("offline"))),
        )

        assertEquals(HomeUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `retry signs in again after a failure`() {
        val repository = FakeAuthRepository(Result.failure(IllegalStateException("offline")))
        val viewModel = HomeViewModel(authRepository = repository)

        repository.result = Result.success("uid-456")
        viewModel.retry()

        assertEquals(HomeUiState.Ready("uid-456"), viewModel.uiState.value)
    }
}

private class FakeAuthRepository(
    var result: Result<String>,
) : AuthRepository {

    override val currentUid: String? = null

    override suspend fun signInAnonymously(): Result<String> = result
}
