package com.huh.app.audio

/** Fixed-capacity PCM buffer whose snapshot is ordered from oldest to newest sample. */
class RollingAudioBuffer private constructor(
    private val samples: ShortArray,
) {
    constructor(capacitySamples: Int) : this(ShortArray(capacitySamples)) {
        require(capacitySamples >= 0) { "Buffer capacity cannot be negative." }
    }

    private var writeIndex = 0
    private var storedSamples = 0

    val capacity: Int
        get() = samples.size

    val size: Int
        get() = storedSamples

    fun append(audio: ShortArray) {
        if (samples.isEmpty() || audio.isEmpty()) return

        val sourceStart = (audio.size - samples.size).coerceAtLeast(0)
        for (index in sourceStart until audio.size) {
            samples[writeIndex] = audio[index]
            writeIndex = (writeIndex + 1) % samples.size
            if (storedSamples < samples.size) storedSamples++
        }
    }

    fun snapshot(): ShortArray {
        if (storedSamples == 0) return ShortArray(0)
        val result = ShortArray(storedSamples)
        val oldestIndex = if (storedSamples == samples.size) writeIndex else 0
        for (index in result.indices) {
            result[index] = samples[(oldestIndex + index) % samples.size]
        }
        return result
    }

    fun clear() {
        writeIndex = 0
        storedSamples = 0
    }

    companion object {
        fun forDuration(sampleRateHz: Int, durationMs: Long): RollingAudioBuffer {
            require(sampleRateHz > 0) { "Sample rate must be positive." }
            require(durationMs >= 0) { "Buffer duration cannot be negative." }
            val sampleCount = Math.multiplyExact(sampleRateHz.toLong(), durationMs) / 1_000L
            require(sampleCount <= Int.MAX_VALUE) { "Requested rolling buffer is too large." }
            return RollingAudioBuffer(sampleCount.toInt())
        }
    }
}
