package com.mobileobie.echo.audio

import com.mobileobie.echo.vad.VoiceActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SilenceChunkAccumulatorTest {
    @Test
    fun emitsAfterConfiguredContinuousQuietBoundary() {
        val accumulator = SilenceChunkAccumulator(
            sampleRateHz = 1_000,
            quietBoundaryMs = 1_000,
            preRollMs = 200,
        )

        assertNull(accumulator.append(samples(300, 1), VoiceActivity.SILENCE))
        assertNull(accumulator.append(samples(100, 2), VoiceActivity.SPEECH))
        assertNull(accumulator.append(samples(500, 3), VoiceActivity.SILENCE))
        val chunk = accumulator.append(samples(500, 4), VoiceActivity.SILENCE)!!

        assertEquals(100, chunk.startMillis)
        assertEquals(1_300, chunk.audio.samples.size)
        assertArrayEquals(samples(200, 1), chunk.audio.samples.copyOfRange(0, 200))
    }

    @Test
    fun dropsLongSilenceBetweenChunksButKeepsShortPreRoll() {
        val accumulator = SilenceChunkAccumulator(
            sampleRateHz = 1_000,
            quietBoundaryMs = 1_000,
            preRollMs = 200,
        )

        accumulator.append(samples(100, 2), VoiceActivity.SPEECH)
        accumulator.append(samples(1_000, 0), VoiceActivity.SILENCE)
        assertNull(accumulator.append(samples(1_000, 0), VoiceActivity.SILENCE))
        assertNull(accumulator.append(samples(100, 5), VoiceActivity.SPEECH))
        val chunk = accumulator.finish()!!

        assertEquals(1_900, chunk.startMillis)
        assertEquals(300, chunk.audio.samples.size)
        assertArrayEquals(samples(200, 0), chunk.audio.samples.copyOfRange(0, 200))
        assertArrayEquals(samples(100, 5), chunk.audio.samples.copyOfRange(200, 300))
    }

    @Test
    fun silenceOnlyAudioDoesNotCreateAWhisperChunk() {
        val accumulator = SilenceChunkAccumulator(1_000, 1_000)

        accumulator.append(samples(5_000, 0), VoiceActivity.SILENCE)

        assertNull(accumulator.finish())
    }

    private fun samples(count: Int, value: Int) = ShortArray(count) { value.toShort() }
}
