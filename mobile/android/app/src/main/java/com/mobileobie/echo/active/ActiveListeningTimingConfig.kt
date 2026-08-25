package com.mobileobie.echo.active

data class ActiveListeningTimingConfig(
    val speechStartThresholdMs: Long = DEFAULT_SPEECH_START_THRESHOLD_MS,
    val minimumTranscriptSpeechMs: Long = DEFAULT_MINIMUM_TRANSCRIPT_SPEECH_MS,
    val conversationEndSilenceMs: Long = DEFAULT_CONVERSATION_END_SILENCE_MS,
    val preRollBufferMs: Long = DEFAULT_PRE_ROLL_BUFFER_MS,
) {
    init {
        require(speechStartThresholdMs > 0) { "Speech-start threshold must be positive." }
        require(conversationEndSilenceMs > 0) { "Conversation-end silence must be positive." }
        require(preRollBufferMs >= 0) { "Pre-roll duration cannot be negative." }
        require(minimumTranscriptSpeechMs >= speechStartThresholdMs) {
            "Minimum conversation speech cannot be shorter than the speech-start threshold."
        }
    }

    companion object {
        const val DEFAULT_SPEECH_START_THRESHOLD_MS = 400L
        const val DEFAULT_MINIMUM_TRANSCRIPT_SPEECH_MS = 3_000L
        const val DEFAULT_CONVERSATION_END_SILENCE_MS = 15_000L
        const val DEFAULT_PRE_ROLL_BUFFER_MS = 2_000L
    }
}
