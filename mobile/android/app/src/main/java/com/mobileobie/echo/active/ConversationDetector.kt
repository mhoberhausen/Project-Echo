package com.mobileobie.echo.active

import com.mobileobie.echo.vad.VoiceActivity

/**
 * Pure conversation-boundary state machine. The owner supplies already-classified audio
 * frame durations and acts on the returned directives.
 */
class ConversationDetector(
    private val timing: ActiveListeningTimingConfig = ActiveListeningTimingConfig(),
) {
    var state: ConversationState = ConversationState.WAITING
        private set

    var cumulativeSpeechDurationMs: Long = 0L
        private set

    var observedSilenceMs: Long = 0L
        private set

    var speechSegmentCount: Int = 0
        private set

    var longestInternalSilenceMs: Long = 0L
        private set

    private var consecutiveStartingSpeechMs: Long = 0L

    fun process(activity: VoiceActivity, frameDurationMs: Long): ConversationUpdate {
        require(frameDurationMs > 0) { "Audio frame duration must be positive." }

        val previousState = state
        var directive = ConversationDirective.NONE
        var endReason: ConversationEndReason? = null

        when (state) {
            ConversationState.WAITING -> {
                if (activity == VoiceActivity.SPEECH) {
                    consecutiveStartingSpeechMs += frameDurationMs
                    if (consecutiveStartingSpeechMs >= timing.speechStartThresholdMs) {
                        cumulativeSpeechDurationMs = consecutiveStartingSpeechMs
                        speechSegmentCount = 1
                        observedSilenceMs = 0L
                        state = ConversationState.LISTENING
                        directive = ConversationDirective.START_CAPTURE
                    }
                } else {
                    consecutiveStartingSpeechMs = 0L
                }
            }

            ConversationState.LISTENING -> {
                if (activity == VoiceActivity.SPEECH) {
                    cumulativeSpeechDurationMs += frameDurationMs
                    observedSilenceMs = 0L
                } else {
                    observedSilenceMs = frameDurationMs
                    state = ConversationState.POSSIBLE_END
                    if (observedSilenceMs >= timing.conversationEndSilenceMs) {
                        endReason = ConversationEndReason.SUSTAINED_SILENCE
                        directive = beginFinalization()
                    }
                }
            }

            ConversationState.POSSIBLE_END -> {
                if (activity == VoiceActivity.SPEECH) {
                    cumulativeSpeechDurationMs += frameDurationMs
                    speechSegmentCount++
                    longestInternalSilenceMs = maxOf(longestInternalSilenceMs, observedSilenceMs)
                    observedSilenceMs = 0L
                    state = ConversationState.LISTENING
                } else {
                    observedSilenceMs += frameDurationMs
                    if (observedSilenceMs >= timing.conversationEndSilenceMs) {
                        endReason = ConversationEndReason.SUSTAINED_SILENCE
                        directive = beginFinalization()
                    }
                }
            }

            ConversationState.FINALIZING -> Unit
        }

        return update(previousState, directive, endReason)
    }

    fun pause(): ConversationUpdate = endCapture(ConversationEndReason.PAUSED)

    fun turnOff(): ConversationUpdate = endCapture(ConversationEndReason.TURNED_OFF)

    /** Call after the current audio has been queued or discarded. */
    fun completeFinalization(): ConversationUpdate {
        check(state == ConversationState.FINALIZING) {
            "Finalization can only complete from the FINALIZING state."
        }
        val previousState = state
        resetToWaiting()
        return update(previousState)
    }

    fun reset(): ConversationUpdate {
        val previousState = state
        resetToWaiting()
        return update(previousState)
    }

    private fun endCapture(reason: ConversationEndReason): ConversationUpdate {
        val previousState = state
        if (state == ConversationState.LISTENING || state == ConversationState.POSSIBLE_END) {
            val directive = beginFinalization()
            return update(previousState, directive, reason)
        }

        if (state == ConversationState.WAITING) resetToWaiting()
        return update(previousState)
    }

    private fun beginFinalization(): ConversationDirective {
        state = ConversationState.FINALIZING
        return if (cumulativeSpeechDurationMs >= timing.minimumTranscriptSpeechMs) {
            ConversationDirective.FINALIZE_CAPTURE
        } else {
            ConversationDirective.DISCARD_CAPTURE
        }
    }

    private fun resetToWaiting() {
        state = ConversationState.WAITING
        cumulativeSpeechDurationMs = 0L
        observedSilenceMs = 0L
        speechSegmentCount = 0
        longestInternalSilenceMs = 0L
        consecutiveStartingSpeechMs = 0L
    }

    private fun update(
        previousState: ConversationState,
        directive: ConversationDirective = ConversationDirective.NONE,
        endReason: ConversationEndReason? = null,
    ) = ConversationUpdate(
        previousState = previousState,
        state = state,
        directive = directive,
        endReason = endReason,
        cumulativeSpeechDurationMs = cumulativeSpeechDurationMs,
        observedSilenceMs = observedSilenceMs,
        speechSegmentCount = speechSegmentCount,
        longestInternalSilenceMs = longestInternalSilenceMs,
    )
}
