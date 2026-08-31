package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.data.SessionRepository
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.active.AutomaticCaptureCleanupPolicy
import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.mobileobie.echo.telemetry.Telemetry
import com.mobileobie.echo.telemetry.TelemetryEvent
import com.mobileobie.echo.telemetry.NoOpTelemetry

class TranscriptionProcessor(
    private val repository: SessionRepository,
    private val transcriber: Transcriber,
    private val diarizer: SpeakerDiarizer,
    private val cleaner: TranscriptCleaner,
    private val cleanupPolicy: () -> AutomaticCaptureCleanupPolicy,
    private val telemetry: Telemetry = NoOpTelemetry,
) {
    suspend fun process(sessionId: String): Boolean {
        repository.refresh()
        val session = repository.sessions.value.firstOrNull { it.id == sessionId } ?: return true
        val path = session.audioPath?.takeIf(String::isNotBlank) ?: return true
        val file = File(path)
        return try {
            val policy = cleanupPolicy()
            repository.updateStatus(session.id, SessionStatus.TRANSCRIBING)
            val audio = RecordedAudio(readPcm16(file), SAMPLE_RATE_HZ)
            val timestamped = session.transcriptSegments.takeIf { it.isNotEmpty() }
                ?.let(::TimestampedTranscript)
                ?: transcriber.transcribe(audio, session.transcriptionModel)
            val diarized = diarizer.diarize(audio, timestamped)
            val original = diarized.text
            val cleaned = cleaner.clean(original)
            if (policy.shouldDiscardTranscript(cleaned)) {
                repository.delete(session.id)
                return true
            }
            repository.saveTranscription(session.id, cleaned, original, diarized.segments)
            file.delete()
            telemetry.record(TelemetryEvent.Processing(TelemetryEvent.Stage.TRANSCRIPTION, TelemetryEvent.Outcome.COMPLETED, durationBucket(audio.samples.size * 1_000L / audio.sampleRateHz)))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: NoSpeechDetectedException) {
            if (cleanupPolicy().minimumTranscriptWords > 0) {
                repository.delete(session.id)
                true
            } else {
                repository.updateStatus(session.id, SessionStatus.TRANSCRIPTION_FAILED)
                false
            }
        } catch (_: Throwable) {
            repository.updateStatus(session.id, SessionStatus.TRANSCRIPTION_FAILED)
            telemetry.record(TelemetryEvent.Processing(TelemetryEvent.Stage.TRANSCRIPTION, TelemetryEvent.Outcome.FAILED, TelemetryEvent.DurationBucket.UNDER_30_SECONDS))
            telemetry.recordSanitizedFailure("background_transcription_failed")
            false
        }
    }

    private fun readPcm16(file: File): ShortArray {
        check(file.exists()) { "The saved conversation audio is missing." }
        val bytes = file.readBytes()
        check(bytes.size >= 2 && bytes.size % 2 == 0) { "The saved conversation audio is invalid." }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also(buffer::get)
    }

    private fun durationBucket(durationMillis: Long): TelemetryEvent.DurationBucket = when {
        durationMillis < 30_000 -> TelemetryEvent.DurationBucket.UNDER_30_SECONDS
        durationMillis < 120_000 -> TelemetryEvent.DurationBucket.UNDER_2_MINUTES
        else -> TelemetryEvent.DurationBucket.OVER_2_MINUTES
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}
