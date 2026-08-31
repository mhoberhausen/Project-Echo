package com.mobileobie.echo.audio

/** Reusable primitive PCM buffer for capture hot paths. */
class PcmSampleBuffer(initialCapacity: Int = 1_024) {
    private var samples = ShortArray(initialCapacity.coerceAtLeast(1))
    private var count = 0

    val size: Int get() = count

    fun write(source: ShortArray, length: Int = source.size) {
        require(length in 0..source.size)
        ensureCapacity(count + length)
        source.copyInto(samples, count, 0, length)
        count += length
    }

    fun take(): ShortArray = samples.copyOf(count).also { count = 0 }

    fun clear() {
        count = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= samples.size) return
        samples = samples.copyOf(maxOf(required, samples.size * 2))
    }
}
