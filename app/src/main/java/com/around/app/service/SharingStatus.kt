package com.around.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SharingStatus {
    data object Stopped : SharingStatus

    data class Running(val since: Long) : SharingStatus

    /** [until] is null when paused until the user resumes manually. */
    data class Paused(val until: Long?) : SharingStatus
}

object SharingRuntime {
    private val _status = MutableStateFlow<SharingStatus>(SharingStatus.Stopped)
    val status: StateFlow<SharingStatus> = _status.asStateFlow()

    fun set(value: SharingStatus) {
        _status.value = value
    }
}
