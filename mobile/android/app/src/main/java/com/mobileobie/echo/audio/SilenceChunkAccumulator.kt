package com.mobileobie.echo.audio

import com.mobileobie.echo.vad.VoiceActivity

/** Builds speech-bearing chunks without retaining long between-utterance silence. */
class SilenceChunkAccumulator(
    private val sampleRateHz: Int,
    quietBoundaryMs: Long,
    private val preRollMs: Long = DEFAULT_PRE_ROLL_MS,
) {
    private val quietBoundarySamples = quietBoundaryMs * sampleRateHz / 1_000L
    private val preRollSamples = (preRollMs * sampleRateHz / 1_000L).toInt()
    private val chunk = PcmSampleBuffer()
    private val preRoll = RollingAudioBuffer(preRollSamples)
    private var samplesObserved = 0L
    private var chunkStartSample = 0L
    private var quietSamples = 0L
    private var hasSpeech = false

    init {
        require(sampleRateHz > 0)
        require(quietBoundaryMs > 0)
        require(preRollMs >= 0)
    }

    fun append(samples: ShortArray, activity: VoiceActivity): RecordedAudioChunk? {
        if (samples.isEmpty()) return null
        val frameStart = samplesObserved
        samplesObserved += samples.size

        if (!hasSpeech && activity == VoiceActivity.SILENCE) {
            preRoll.append(samples)
            return null
        }

        if (!hasSpeech) {
            chunkStartSample = (frameStart - preRoll.size).coerceAtLeast(0)
            chunk.write(preRoll.snapshot())
            preRoll.clear()
            hasSpeech = true
        }
        chunk.write(samples)

        quietSamples = if (activity == VoiceActivity.SILENCE) quietSamples + samples.size else 0L
        if (quietSamples < quietBoundarySamples) return null
        return emitChunk()
    }

    fun finish(): RecordedAudioChunk? = if (hasSpeech && chunk.size > 0) emitChunk() else null

    private fun emitChunk(): RecordedAudioChunk {
        val result = RecordedAudioChunk(
            audio = RecordedAudio(chunk.take(), sampleRateHz),
            startMillis = chunkStartSample * 1_000L / sampleRateHz,
        )
        hasSpeech = false
        quietSamples = 0
        preRoll.clear()
        return result
    }

    companion object {
        const val DEFAULT_PRE_ROLL_MS = 200L
    }
}
