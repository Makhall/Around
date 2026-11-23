package com.around.app.feature.circles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.around.app.data.circles.CircleRepository
import com.around.app.data.model.CircleError
import com.around.app.data.model.toCircleError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CreateCircleViewModel @Inject constructor(
    private val circleRepository: CircleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCircleUiState())
    val uiState: StateFlow<CreateCircleUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) {
        _uiState.update {
            it.copy(name = value.take(40), error = null)
        }
    }

    fun create() {
        val name = _uiState.value.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = CircleError.INVALID_NAME) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, error = null) }
            circleRepository.createCircle(name).fold(
                onSuccess = { created ->
                    _uiState.update {
                        it.copy(submitting = false, created = created)
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(submitting = false, error = throwable.toCircleError())
                    }
                },
            )
        }
    }
}
