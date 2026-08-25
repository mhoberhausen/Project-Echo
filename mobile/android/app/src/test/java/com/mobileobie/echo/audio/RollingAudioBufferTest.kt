package com.mobileobie.echo.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RollingAudioBufferTest {
    @Test
    fun retainsSamplesInArrivalOrderBeforeCapacity() {
        val buffer = RollingAudioBuffer(5)

        buffer.append(shortArrayOf(1, 2))
        buffer.append(shortArrayOf(3))

        assertEquals(3, buffer.size)
        assertArrayEquals(shortArrayOf(1, 2, 3), buffer.snapshot())
    }

    @Test
    fun overwritesOldestSamplesAtCapacity() {
        val buffer = RollingAudioBuffer(5)

        buffer.append(shortArrayOf(1, 2, 3))
        buffer.append(shortArrayOf(4, 5, 6, 7))

        assertEquals(5, buffer.size)
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), buffer.snapshot())
    }

    @Test
    fun oversizedAppendKeepsOnlyNewestSamples() {
        val buffer = RollingAudioBuffer(3)

        buffer.append(shortArrayOf(1, 2, 3, 4, 5))

        assertArrayEquals(shortArrayOf(3, 4, 5), buffer.snapshot())
    }

    @Test
    fun clearRemovesBufferedAudio() {
        val buffer = RollingAudioBuffer(3)
        buffer.append(shortArrayOf(1, 2, 3))

        buffer.clear()

        assertEquals(0, buffer.size)
        assertArrayEquals(shortArrayOf(), buffer.snapshot())
    }

    @Test
    fun durationFactoryUsesSampleRateAndMilliseconds() {
        val buffer = RollingAudioBuffer.forDuration(sampleRateHz = 16_000, durationMs = 3_000)

        assertEquals(48_000, buffer.capacity)
    }
}
