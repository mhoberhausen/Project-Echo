package com.mobileobie.echo.active

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveListeningTimingConfigTest {
    @Test
    fun usesEvidenceBasedProductDefaults() {
        val timing = ActiveListeningTimingConfig()
        assertEquals(400, timing.speechStartThresholdMs)
        assertEquals(3_000, timing.minimumTranscriptSpeechMs)
        assertEquals(15_000, timing.conversationEndSilenceMs)
        assertEquals(1_000, timing.quietBoundaryMs)
        assertEquals(2_000, timing.preRollBufferMs)
    }

    @Test
    fun rejectsInvalidTimingValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ActiveListeningTimingConfig(speechStartThresholdMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActiveListeningTimingConfig(preRollBufferMs = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActiveListeningTimingConfig(quietBoundaryMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActiveListeningTimingConfig(
                speechStartThresholdMs = 500,
                minimumTranscriptSpeechMs = 499,
            )
        }
    }
}
