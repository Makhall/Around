package com.around.app.feature.profile

import com.around.app.FakeAuthRepository
import com.around.app.FakeUserRepository
import com.around.app.MainDispatcherRule
import com.around.app.data.model.Avatar
import com.around.app.data.model.AvatarType
import com.around.app.testProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the existing profile`() {
        val viewModel = ProfileViewModel(
            authRepository = FakeAuthRepository(),
            userRepository = FakeUserRepository(profile = testProfile),
        )

        assertEquals(false, viewModel.uiState.value.loading)
        assertEquals("Alice", viewModel.uiState.value.name)
        assertEquals(testProfile.avatar, viewModel.uiState.value.avatar)
    }

    @Test
    fun `rejects an empty name`() {
        val viewModel = ProfileViewModel(
            authRepository = FakeAuthRepository(),
            userRepository = FakeUserRepository(profile = null),
        )

        viewModel.save()

        assertEquals(ProfileError.NAME_REQUIRED, viewModel.uiState.value.error)
    }

    @Test
    fun `saves a trimmed profile and avatar`() {
        val repository = FakeUserRepository(profile = null)
        val viewModel = ProfileViewModel(
            authRepository = FakeAuthRepository(),
            userRepository = repository,
        )

        viewModel.onNameChange("  Alice  ")
        viewModel.onAvatarChange(Avatar(AvatarType.EMOJI, "🦊"))
        viewModel.save()

        assertTrue(viewModel.uiState.value.saved)
        assertEquals(1, repository.savedProfiles.size)
        assertEquals("Alice", repository.savedProfiles.single().displayName)
        assertEquals(Avatar(AvatarType.EMOJI, "🦊"), repository.savedProfiles.single().avatar)
    }
}
