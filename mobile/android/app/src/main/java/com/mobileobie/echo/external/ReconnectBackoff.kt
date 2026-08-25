package com.mobileobie.echo.external

import kotlin.random.Random

class ReconnectBackoff(
    private val baseDelayMs: Long = 1_000L,
    private val maximumDelayMs: Long = 30_000L,
    private val random: Random = Random.Default,
) {
    fun delayMs(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 4)
        val withoutJitter = (baseDelayMs * (1L shl exponent)).coerceAtMost(maximumDelayMs)
        val jitter = if (withoutJitter < 4) 0 else random.nextLong(withoutJitter / 4 + 1)
        return (withoutJitter + jitter).coerceAtMost(maximumDelayMs)
    }
}
