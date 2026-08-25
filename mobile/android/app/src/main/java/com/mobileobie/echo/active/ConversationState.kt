package com.mobileobie.echo.active

enum class ConversationState {
    WAITING,
    LISTENING,
    POSSIBLE_END,
    FINALIZING,
}

enum class ConversationDirective {
    NONE,
    START_CAPTURE,
    FINALIZE_CAPTURE,
    DISCARD_CAPTURE,
}

enum class ConversationEndReason {
    SUSTAINED_SILENCE,
    PAUSED,
    TURNED_OFF,
}

data class ConversationUpdate(
    val previousState: ConversationState,
    val state: ConversationState,
    val directive: ConversationDirective = ConversationDirective.NONE,
    val endReason: ConversationEndReason? = null,
    val cumulativeSpeechDurationMs: Long = 0L,
    val observedSilenceMs: Long = 0L,
    val speechSegmentCount: Int = 0,
    val longestInternalSilenceMs: Long = 0L,
) {
    val stateChanged: Boolean
        get() = previousState != state
}
