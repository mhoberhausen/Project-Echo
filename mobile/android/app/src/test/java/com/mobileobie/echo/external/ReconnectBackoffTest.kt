package com.mobileobie.echo.external

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun growsExponentiallyWithBoundedJitterAndCap() {
        val backoff = ReconnectBackoff(random = Random(7))
        val delays = (1..8).map(backoff::delayMs)

        assertTrue(delays[0] in 1_000L..1_250L)
        assertTrue(delays[1] in 2_000L..2_500L)
        assertTrue(delays[2] in 4_000L..5_000L)
        assertTrue(delays[3] in 8_000L..10_000L)
        assertTrue(delays.drop(5).all { it <= 30_000L })
        assertEquals(8, delays.size)
    }
}
