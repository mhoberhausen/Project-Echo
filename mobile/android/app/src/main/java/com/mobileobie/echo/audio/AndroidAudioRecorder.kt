package com.mobileobie.echo.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import com.mobileobie.echo.vad.HeuristicVoiceActivityDetector
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AndroidAudioRecorder(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioRecorder, IncrementalAudioRecorder {
    private val lock = Any()
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var pcmBytes = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    override suspend fun start() = startInternal(null, null)

    override suspend fun startIncremental(
        quietBoundaryMs: Long,
        onChunk: (RecordedAudioChunk) -> Unit,
    ) = startInternal(quietBoundaryMs, onChunk)

    @SuppressLint("MissingPermission")
    private suspend fun startInternal(
        quietBoundaryMs: Long?,
        onChunk: ((RecordedAudioChunk) -> Unit)?,
    ) {
        check(audioRecord == null) { "A recording is already in progress." }
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "This device cannot record 16 kHz mono audio." }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minimum * 2,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Could not initialize the microphone."
        }

        synchronized(lock) { pcmBytes = ByteArrayOutputStream() }
        audioRecord = recorder
        recorder.startRecording()
        val chunker = quietBoundaryMs?.let {
            SilenceChunkAccumulator(SAMPLE_RATE_HZ, it)
        }
        val chunkVad = if (chunker != null) {
            HeuristicVoiceActivityDetector(
                HeuristicVoiceActivityDetector.Config(speechHangoverFrames = 0),
            )
        } else null
        captureJob = CoroutineScope(ioDispatcher).launch {
            val buffer = ShortArray(FRAME_SAMPLES)
            while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count > 0) {
                    val frame = buffer.copyOf(count)
                    val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                    frame.forEach(bytes::putShort)
                    synchronized(lock) { pcmBytes.write(bytes.array()) }
                    chunker?.append(frame, chunkVad!!.process(frame))?.let { onChunk?.invoke(it) }
                }
            }
            chunker?.finish()?.let { onChunk?.invoke(it) }
        }
    }

    override suspend fun stop(): RecordedAudio {
        val recorder = checkNotNull(audioRecord) { "No recording is in progress." }
        recorder.stop()
        captureJob?.cancelAndJoin()
        captureJob = null
        recorder.release()
        audioRecord = null

        val bytes = synchronized(lock) { pcmBytes.toByteArray() }
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        return RecordedAudio(shorts, SAMPLE_RATE_HZ)
    }

    override fun release() {
        audioRecord?.runCatching { stop() }
        audioRecord?.release()
        audioRecord = null
        captureJob?.cancel()
        captureJob = null
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_SAMPLES = 320
    }
}
