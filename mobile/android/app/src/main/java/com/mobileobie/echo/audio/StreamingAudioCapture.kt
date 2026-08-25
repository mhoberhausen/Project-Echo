package com.mobileobie.echo.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.mobileobie.echo.model.SessionSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

class StreamingAudioCapture : StreamingPcmSource {
    @Volatile private var recorder: AudioRecord? = null

    override val descriptor = PcmSourceDescriptor(sessionSource = SessionSource.ACTIVE_LISTENING)

    @SuppressLint("MissingPermission")
    override suspend fun capture(onEvent: (PcmSourceEvent) -> Unit) {
        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBuffer > 0) { "This device cannot initialize microphone capture." }
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimumBuffer, FRAME_SAMPLES * 4))
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            "The microphone is unavailable."
        }
        recorder = audioRecord
        try {
            audioRecord.startRecording()
            check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "The microphone could not start."
            }
            onEvent(PcmSourceEvent.Ready(descriptor))
            onEvent(PcmSourceEvent.StreamStarted(null))
            val buffer = ShortArray(FRAME_SAMPLES)
            while (currentCoroutineContext().isActive) {
                val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    count > 0 -> onEvent(PcmSourceEvent.Audio(buffer.copyOf(count)))
                    count == AudioRecord.ERROR_DEAD_OBJECT -> throw AudioRouteChangedException()
                    count < 0 -> error("Microphone capture failed ($count).")
                }
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
            recorder = null
        }
    }

    override fun stop() {
        runCatching { recorder?.stop() }
    }

    companion object {
        const val SAMPLE_RATE_HZ = CANONICAL_SAMPLE_RATE_HZ
        const val FRAME_DURATION_MS = CANONICAL_FRAME_DURATION_MS
        const val FRAME_SAMPLES = CANONICAL_FRAME_SAMPLES
    }
}

class AudioRouteChangedException : IllegalStateException("The audio route changed.")
