package com.mobileobie.echo.external.protocol

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSequenceTrackerTest {
    private val streamId = UUID.randomUUID()

    @Test
    fun insertsBoundedSilenceAndMarksGapDegraded() {
        val tracker = AudioSequenceTracker().apply { start(streamId) }
        tracker.accept(audio(sequence = 0, firstSample = 0))

        val result = tracker.accept(audio(sequence = 3, firstSample = 960))

        assertEquals(2, result.insertedGapFrames)
        assertEquals(3, result.frames.size)
        assertTrue(result.frames[0].all { it == 0.toShort() })
        assertTrue(result.degraded)
    }

    @Test
    fun ignoresDuplicateFrame() {
        val tracker = AudioSequenceTracker().apply { start(streamId) }
        val message = audio(sequence = 0, firstSample = 0)
        tracker.accept(message)

        val duplicate = tracker.accept(message)

        assertTrue(duplicate.duplicate)
        assertEquals(0, duplicate.frames.size)
    }

    @Test
    fun interruptsOnGapLargerThanTwoHundredMilliseconds() {
        val tracker = AudioSequenceTracker().apply { start(streamId) }

        assertThrows(StreamInterruptedException::class.java) {
            tracker.accept(audio(sequence = 11, firstSample = 3_520))
        }
    }

    @Test
    fun rejectsSequenceAndSamplePositionDisagreement() {
        val tracker = AudioSequenceTracker().apply { start(streamId) }

        assertThrows(ProtocolException::class.java) {
            tracker.accept(audio(sequence = 2, firstSample = 320))
        }
    }

    private fun audio(sequence: Long, firstSample: Long) = HuhAudioMessage.Audio(
        streamId = streamId,
        sequence = sequence,
        firstSampleIndex = firstSample,
        pcm = ShortArray(320) { 1 },
    )
}
