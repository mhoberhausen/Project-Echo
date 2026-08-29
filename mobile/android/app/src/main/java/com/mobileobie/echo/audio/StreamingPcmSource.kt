package com.mobileobie.echo.audio

import com.mobileobie.echo.model.SessionSource
import java.io.File

/** A cancellable source of canonical 16 kHz, mono, PCM16 frames. */
interface StreamingPcmSource {
    val descriptor: PcmSourceDescriptor
    val diagnostics: PcmSourceDiagnostics get() = PcmSourceDiagnostics()
    suspend fun capture(onEvent: (PcmSourceEvent) -> Unit)
    fun stop()
}

sealed interface PcmSourceEvent {
    data class Ready(val descriptor: PcmSourceDescriptor) : PcmSourceEvent
    data class StreamStarted(val streamId: String?) : PcmSourceEvent
    data class Audio(val samples: ShortArray) : PcmSourceEvent
    /** Complete canonical PCM file. Consumers must copy it before this callback returns. */
    data class FinalizedAudio(val file: File, val durationMillis: Long) : PcmSourceEvent
    data class StreamStopped(val reason: PcmSourceEndReason) : PcmSourceEvent
    data class Disconnected(val message: String?) : PcmSourceEvent
}

enum class PcmSourceEndReason {
    USER_STOP,
    PAUSE,
    DISCONNECT,
    STORAGE_FAILURE,
    DEVICE_REBOOT,
    CAPTURE_FAILURE,
    UNKNOWN,
}

data class PcmSourceDiagnostics(
    val streamId: String? = null,
    val gapCount: Int = 0,
    val insertedSilenceSamples: Long = 0L,
    val receiverOverrunCount: Int = 0,
    val interrupted: Boolean = false,
)

data class PcmSourceDescriptor(
    val sessionSource: SessionSource,
    val sampleRateHz: Int = CANONICAL_SAMPLE_RATE_HZ,
    val frameDurationMs: Long = CANONICAL_FRAME_DURATION_MS,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmwareVersion: String? = null,
    val protocolVersion: Int? = null,
    val transport: String? = null,
) {
    init {
        require(sampleRateHz == CANONICAL_SAMPLE_RATE_HZ) { "Only 16 kHz PCM is supported." }
        require(frameDurationMs == CANONICAL_FRAME_DURATION_MS) { "Only 20 ms frames are supported." }
    }
}

const val CANONICAL_SAMPLE_RATE_HZ = 16_000
const val CANONICAL_FRAME_DURATION_MS = 20L
const val CANONICAL_FRAME_SAMPLES = 320
