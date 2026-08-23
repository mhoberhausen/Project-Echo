package com.huh.app.active

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ActiveListeningState {
    OFF,
    STARTING,
    WAITING,
    LISTENING,
    PAUSED,
    ERROR,
}

data class ActiveListeningSnapshot(
    val state: ActiveListeningState = ActiveListeningState.OFF,
    val errorMessage: String? = null,
    val captureStartedAtUtcMillis: Long? = null,
)

object ActiveListeningRuntime {
    private val _snapshot = MutableStateFlow(ActiveListeningSnapshot())
    val snapshot: StateFlow<ActiveListeningSnapshot> = _snapshot.asStateFlow()

    fun update(
        state: ActiveListeningState,
        errorMessage: String? = null,
        captureStartedAtUtcMillis: Long? = null,
    ) {
        _snapshot.value = ActiveListeningSnapshot(state, errorMessage, captureStartedAtUtcMillis)
    }
}
