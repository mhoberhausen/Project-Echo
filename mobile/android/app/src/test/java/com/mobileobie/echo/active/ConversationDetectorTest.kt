package com.mobileobie.echo.active

import com.mobileobie.echo.vad.VoiceActivity.SILENCE
import com.mobileobie.echo.vad.VoiceActivity.SPEECH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationDetectorTest {
    private val timing = ActiveListeningTimingConfig(
        speechStartThresholdMs = 500,
        conversationEndSilenceMs = 1_000,
        preRollBufferMs = 3_000,
        minimumTranscriptSpeechMs = 1_000,
    )

    @Test
    fun requiresSustainedSpeechBeforeStartingConversation() {
        val detector = ConversationDetector(timing)

        assertEquals(ConversationDirective.NONE, detector.process(SPEECH, 250).directive)
        detector.process(SILENCE, 100)
        assertEquals(ConversationDirective.NONE, detector.process(SPEECH, 250).directive)

        val started = detector.process(SPEECH, 250)
        assertEquals(ConversationState.LISTENING, started.state)
        assertEquals(ConversationDirective.START_CAPTURE, started.directive)
        assertEquals(500, started.cumulativeSpeechDurationMs)
        assertEquals(1, started.speechSegmentCount)
    }

    @Test
    fun resumedSpeechCancelsPossibleEndAndContinuesSameConversation() {
        val detector = startedDetector()

        val possibleEnd = detector.process(SILENCE, 750)
        assertEquals(ConversationState.POSSIBLE_END, possibleEnd.state)
        assertEquals(750, possibleEnd.observedSilenceMs)

        val resumed = detector.process(SPEECH, 250)
        assertEquals(ConversationState.LISTENING, resumed.state)
        assertEquals(ConversationDirective.NONE, resumed.directive)
        assertEquals(0, resumed.observedSilenceMs)
        assertEquals(1_000, resumed.cumulativeSpeechDurationMs)
        assertEquals(2, resumed.speechSegmentCount)
        assertEquals(750, resumed.longestInternalSilenceMs)
    }

    @Test
    fun sustainedSilenceFinalizesConversationMeetingMinimumSpeech() {
        val detector = startedDetector()
        detector.process(SPEECH, 500)

        detector.process(SILENCE, 500)
        val finalizing = detector.process(SILENCE, 500)

        assertEquals(ConversationState.FINALIZING, finalizing.state)
        assertEquals(ConversationDirective.FINALIZE_CAPTURE, finalizing.directive)
        assertEquals(ConversationEndReason.SUSTAINED_SILENCE, finalizing.endReason)

        val waiting = detector.completeFinalization()
        assertEquals(ConversationState.WAITING, waiting.state)
        assertEquals(0, waiting.cumulativeSpeechDurationMs)
        assertEquals(0, waiting.observedSilenceMs)
    }

    @Test
    fun sustainedSilenceDiscardsConversationBelowMinimumSpeech() {
        val detector = startedDetector()

        val finalizing = detector.process(SILENCE, 1_000)

        assertEquals(ConversationState.FINALIZING, finalizing.state)
        assertEquals(ConversationDirective.DISCARD_CAPTURE, finalizing.directive)
        assertEquals(ConversationEndReason.SUSTAINED_SILENCE, finalizing.endReason)
    }

    @Test
    fun pauseFinalizesMeaningfulConversation() {
        val detector = startedDetector()
        detector.process(SPEECH, 500)

        val result = detector.pause()

        assertEquals(ConversationState.FINALIZING, result.state)
        assertEquals(ConversationDirective.FINALIZE_CAPTURE, result.directive)
        assertEquals(ConversationEndReason.PAUSED, result.endReason)
    }

    @Test
    fun pauseDiscardsTooShortConversation() {
        val detector = startedDetector()

        val result = detector.pause()

        assertEquals(ConversationDirective.DISCARD_CAPTURE, result.directive)
        assertEquals(ConversationEndReason.PAUSED, result.endReason)
    }

    @Test
    fun longRecordingDoesNotQualifyWithoutEnoughCumulativeSpeech() {
        val detector = startedDetector() // 750 ms speech, below the 1,000 ms minimum.
        detector.process(SILENCE, 900)

        val result = detector.pause()

        assertEquals(ConversationDirective.DISCARD_CAPTURE, result.directive)
        assertEquals(750, result.cumulativeSpeechDurationMs)
        assertEquals(900, result.observedSilenceMs)
    }

    @Test
    fun turnOffDiscardsTooShortConversation() {
        val detector = startedDetector()

        val result = detector.turnOff()

        assertEquals(ConversationState.FINALIZING, result.state)
        assertEquals(ConversationDirective.DISCARD_CAPTURE, result.directive)
        assertEquals(ConversationEndReason.TURNED_OFF, result.endReason)
    }

    @Test
    fun turnOffFinalizesMeaningfulConversation() {
        val detector = startedDetector()
        detector.process(SPEECH, 500)

        val result = detector.turnOff()

        assertEquals(ConversationDirective.FINALIZE_CAPTURE, result.directive)
        assertEquals(ConversationEndReason.TURNED_OFF, result.endReason)
    }

    @Test
    fun pauseWhileWaitingDoesNotCreateConversation() {
        val detector = ConversationDetector(timing)
        detector.process(SPEECH, 250)

        val result = detector.pause()

        assertEquals(ConversationState.WAITING, result.state)
        assertEquals(ConversationDirective.NONE, result.directive)
        assertEquals(0, result.cumulativeSpeechDurationMs)
    }

    @Test
    fun ignoresFramesWhileCallerCompletesFinalization() {
        val detector = startedDetector()
        detector.process(SILENCE, 1_000)

        val ignored = detector.process(SPEECH, 500)

        assertEquals(ConversationState.FINALIZING, ignored.state)
        assertEquals(ConversationDirective.NONE, ignored.directive)
    }

    @Test
    fun rejectsInvalidFrameDurationAndInvalidCompletion() {
        val detector = ConversationDetector(timing)
        assertThrows(IllegalArgumentException::class.java) { detector.process(SPEECH, 0) }
        assertThrows(IllegalStateException::class.java) { detector.completeFinalization() }
    }

    private fun startedDetector(): ConversationDetector = ConversationDetector(timing).also {
        it.process(SPEECH, 250)
        it.process(SPEECH, 250)
        it.process(SPEECH, 250)
    }
}
