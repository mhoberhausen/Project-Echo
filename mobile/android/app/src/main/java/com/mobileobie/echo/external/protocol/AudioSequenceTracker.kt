package com.mobileobie.echo.external.protocol

import com.mobileobie.echo.audio.CANONICAL_FRAME_SAMPLES

data class SequencedAudio(
    val frames: List<ShortArray>,
    val insertedGapFrames: Int = 0,
    val duplicate: Boolean = false,
) {
    val degraded: Boolean get() = insertedGapFrames > 0
}

class AudioSequenceTracker(private val maximumRecoverableGapSamples: Long = 3_200L) {
    private var streamId: java.util.UUID? = null
    private var nextSequence = 0L
    private var nextSampleIndex = 0L

    fun start(streamId: java.util.UUID) {
        this.streamId = streamId
        nextSequence = 0L
        nextSampleIndex = 0L
    }

    fun accept(message: HuhAudioMessage.Audio): SequencedAudio {
        if (message.streamId != streamId) throw ProtocolException("AUDIO belongs to a different stream.")
        if (message.sequence < nextSequence) return SequencedAudio(emptyList(), duplicate = true)
        if (message.firstSampleIndex < nextSampleIndex) return SequencedAudio(emptyList(), duplicate = true)
        val missingSequenceFrames = message.sequence - nextSequence
        val missingSamples = message.firstSampleIndex - nextSampleIndex
        if (missingSamples % CANONICAL_FRAME_SAMPLES != 0L) {
            throw ProtocolException("Audio sample position is not frame-aligned.")
        }
        if (missingSamples > maximumRecoverableGapSamples) {
            throw StreamInterruptedException("Audio gap of $missingSamples samples is too large to repair.")
        }
        val gapFrames = (missingSamples / CANONICAL_FRAME_SAMPLES).toInt()
        if (missingSequenceFrames != gapFrames.toLong()) {
            throw ProtocolException("Sequence and sample position disagree about the audio gap.")
        }
        val frames = buildList {
            repeat(gapFrames) { add(ShortArray(CANONICAL_FRAME_SAMPLES)) }
            add(message.pcm)
        }
        nextSequence = message.sequence + 1
        nextSampleIndex = message.firstSampleIndex + message.pcm.size
        return SequencedAudio(frames, insertedGapFrames = gapFrames)
    }
}

class StreamInterruptedException(message: String) : IllegalStateException(message)
