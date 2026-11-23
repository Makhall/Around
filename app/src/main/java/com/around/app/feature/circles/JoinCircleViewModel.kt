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
class JoinCircleViewModel @Inject constructor(
    private val circleRepository: CircleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinCircleUiState())
    val uiState: StateFlow<JoinCircleUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) {
        val normalized = value
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .take(8)
        _uiState.update {
            it.copy(code = normalized, error = null)
        }
    }

    fun join() {
        val code = _uiState.value.code
        if (code.length != 8) {
            _uiState.update { it.copy(error = CircleError.CODE_REQUIRED) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(joining = true, error = null) }
            circleRepository.joinCircle(code).fold(
                onSuccess = { joined ->
                    _uiState.update {
                        it.copy(joining = false, joined = joined)
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(joining = false, error = throwable.toCircleError())
                    }
                },
            )
        }
    }
}
