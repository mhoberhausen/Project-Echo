package com.mobileobie.echo.active

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ActiveListeningState {
    OFF,
    STARTING,
    RECONNECTING,
    WAITING,
    LISTENING,
    PAUSED,
    ERROR,
}

enum class ActiveListeningSource { PHONE, EXTERNAL_DEVICE }

data class ActiveListeningSnapshot(
    val state: ActiveListeningState = ActiveListeningState.OFF,
    val errorMessage: String? = null,
    val captureStartedAtUtcMillis: Long? = null,
    val source: ActiveListeningSource = ActiveListeningSource.PHONE,
    val sourceName: String? = null,
    val reconnectAttempt: Int = 0,
)

object ActiveListeningRuntime {
    private val _snapshot = MutableStateFlow(ActiveListeningSnapshot())
    val snapshot: StateFlow<ActiveListeningSnapshot> = _snapshot.asStateFlow()

    fun update(
        state: ActiveListeningState,
        errorMessage: String? = null,
        captureStartedAtUtcMillis: Long? = null,
        source: ActiveListeningSource = ActiveListeningSource.PHONE,
        sourceName: String? = null,
        reconnectAttempt: Int = 0,
    ) {
        _snapshot.value = ActiveListeningSnapshot(
            state, errorMessage, captureStartedAtUtcMillis, source, sourceName, reconnectAttempt
        )
    }
}
