package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudioChunk
import com.mobileobie.echo.model.TranscriptionModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Bounded, single-consumer queue for transcribing completed chunks while capture continues. */
class IncrementalTranscriptionWorker(
    scope: CoroutineScope,
    private val transcriber: Transcriber,
    private val model: TranscriptionModel,
    backlogCapacity: Int = DEFAULT_BACKLOG_CAPACITY,
    private val onDiagnostic: (String) -> Unit = {},
) {
    private val channel = Channel<RecordedAudioChunk>(backlogCapacity)
    private val transcripts = mutableListOf<TimestampedTranscript>()
    @Volatile private var failed = false
    private val job: Job = scope.launch {
        try {
            for (chunk in channel) {
                try {
                    onDiagnostic(
                        "Transcribing chunk at ${chunk.startMillis}ms " +
                            "for ${chunk.audio.durationMillis()}ms",
                    )
                    transcripts += transcriber.transcribe(chunk.audio, model).offsetBy(chunk.startMillis)
                    onDiagnostic("Chunk complete; total=${transcripts.size}")
                } catch (_: NoSpeechDetectedException) {
                    // A VAD/Whisper disagreement can otherwise silently omit spoken words.
                    failed = true
                    onDiagnostic("Whisper found no speech in one VAD-positive chunk; falling back to full audio")
                    channel.cancel()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    failed = true
                    onDiagnostic("Chunk transcription failed; falling back to full audio")
                    channel.cancel()
                }
            }
        } catch (_: CancellationException) {
            // Explicit cancellation or backlog fallback.
        }
    }

    fun offer(chunk: RecordedAudioChunk): Boolean {
        if (failed) return false
        if (channel.trySend(chunk).isSuccess) {
            onDiagnostic("Queued chunk at ${chunk.startMillis}ms")
            return true
        }
        failed = true
        onDiagnostic("Chunk backlog full; falling back to full audio")
        channel.cancel()
        return false
    }

    suspend fun finish(): TimestampedTranscript? {
        channel.close()
        job.join()
        return if (failed || transcripts.isEmpty()) {
            onDiagnostic("Incremental result unavailable; full-audio transcription required")
            null
        } else {
            onDiagnostic("Incremental transcription complete; chunks=${transcripts.size}")
            TimestampedTranscript(transcripts.flatMap { it.segments })
        }
    }

    fun cancel() {
        failed = true
        channel.cancel()
        job.cancel()
    }

    private fun TimestampedTranscript.offsetBy(offsetMillis: Long) = TimestampedTranscript(
        segments = segments.map {
            it.copy(
                startMillis = it.startMillis + offsetMillis,
                endMillis = it.endMillis + offsetMillis,
            )
        },
    )

    companion object {
        const val DEFAULT_BACKLOG_CAPACITY = 4
    }
}

private fun com.mobileobie.echo.audio.RecordedAudio.durationMillis(): Long =
    samples.size * 1_000L / sampleRateHz
