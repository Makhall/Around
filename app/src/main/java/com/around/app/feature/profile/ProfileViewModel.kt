package com.around.app.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.around.app.data.auth.AuthRepository
import com.around.app.data.model.Avatar
import com.around.app.data.model.UserProfile
import com.around.app.data.model.UserSettings
import com.around.app.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val uid: String? = authRepository.currentUid
    private var existingProfile: UserProfile? = null

    init {
        load()
    }

    fun onNameChange(value: String) {
        _uiState.update {
            it.copy(name = value.take(30), error = null)
        }
    }

    fun onAvatarChange(avatar: Avatar) {
        _uiState.update {
            it.copy(avatar = avatar, error = null)
        }
    }

    fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = ProfileError.NAME_REQUIRED) }
            return
        }

        val currentUid = uid
        if (currentUid == null) {
            _uiState.update { it.copy(error = ProfileError.LOAD_FAILED) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            val profile = UserProfile(
                uid = currentUid,
                displayName = name,
                avatar = state.avatar,
                locale = currentLocale(),
                createdAt = existingProfile?.createdAt ?: 0L,
                settings = existingProfile?.settings ?: UserSettings(),
            )

            userRepository.saveProfile(profile).fold(
                onSuccess = {
                    existingProfile = profile
                    _uiState.update { it.copy(saving = false, saved = true) }
                },
                onFailure = { throwable ->
                    Log.w(TAG, "Profile save failed", throwable)
                    _uiState.update {
                        it.copy(saving = false, error = ProfileError.SAVE_FAILED)
                    }
                },
            )
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun retryLoad() {
        _uiState.update { it.copy(loading = true, error = null) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val currentUid = uid
            if (currentUid == null) {
                _uiState.value = ProfileUiState(
                    loading = false,
                    error = ProfileError.LOAD_FAILED,
                )
                return@launch
            }

            runCatching { userRepository.getProfile(currentUid) }
                .onSuccess { profile ->
                    existingProfile = profile
                    _uiState.value = ProfileUiState(
                        loading = false,
                        name = profile?.displayName.orEmpty(),
                        avatar = profile?.avatar ?: Avatar.Default,
                    )
                }
                .onFailure { throwable ->
                    Log.w(TAG, "Profile load failed", throwable)
                    _uiState.value = ProfileUiState(
                        loading = false,
                        error = ProfileError.LOAD_FAILED,
                    )
                }
        }
    }

    private fun currentLocale(): String {
        return if (Locale.getDefault().language == "in") "in" else "en"
    }

    private companion object {
        const val TAG = "ProfileViewModel"
    }
}
