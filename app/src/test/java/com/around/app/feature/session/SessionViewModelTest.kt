package com.around.app.feature.session

import com.around.app.FakeAuthRepository
import com.around.app.FakeUserRepository
import com.around.app.MainDispatcherRule
import com.around.app.testProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `existing profile goes ready`() {
        val viewModel = SessionViewModel(
            authRepository = FakeAuthRepository(Result.success("uid-alice")),
            userRepository = FakeUserRepository(profile = testProfile),
        )

        assertEquals(SessionUiState.Ready, viewModel.uiState.value)
    }

    @Test
    fun `missing profile asks for onboarding`() {
        val viewModel = SessionViewModel(
            authRepository = FakeAuthRepository(Result.success("uid-alice")),
            userRepository = FakeUserRepository(profile = null),
        )

        assertEquals(SessionUiState.NeedsProfile("uid-alice"), viewModel.uiState.value)
    }

    @Test
    fun `sign-in failure shows error`() {
        val viewModel = SessionViewModel(
            authRepository = FakeAuthRepository(Result.failure(IllegalStateException("offline"))),
            userRepository = FakeUserRepository(profile = null),
        )

        assertEquals(SessionUiState.Error, viewModel.uiState.value)
    }
}
