package com.mobileobie.echo.transcription

import android.util.Log
import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.data.SessionRepository
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.ProcessingStage
import com.mobileobie.echo.model.StageProgress
import com.mobileobie.echo.model.StageState
import com.mobileobie.echo.model.ProcessingFailure
import com.mobileobie.echo.active.AutomaticCaptureCleanupPolicy
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.BufferedInputStream
import java.io.FileInputStream
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
            if (!file.isFile) {
                return markTranscriptionFailed(
                    session,
                    ProcessingFailure(
                        stage = ProcessingStage.TRANSCRIPTION,
                        retryable = false,
                        userMessage = "The recording file is no longer available, so it cannot be transcribed.",
                        debugCode = FAILURE_SOURCE_AUDIO_MISSING,
                    ),
                )
            }
            val policy = cleanupPolicy()
            repository.updateStatus(session.id, SessionStatus.TRANSCRIBING)
            repository.updateProcessing(session.id, session.processing.withStage(
                ProcessingStage.TRANSCRIPTION,
                StageProgress(StageState.RUNNING, session.processing.stage(ProcessingStage.TRANSCRIPTION).attempts + 1, startedAtUtcMillis = System.currentTimeMillis()),
            ))
            val audio = RecordedAudio(readPcm16(file), SAMPLE_RATE_HZ)
            val timestamped = session.transcriptSegments.takeIf { it.isNotEmpty() }
                ?.let(::TimestampedTranscript)
                ?: transcriber.transcribe(audio, session.transcriptionModel)
            val transcribed = session.processing.withStage(ProcessingStage.TRANSCRIPTION,
                StageProgress(StageState.COMPLETE, session.processing.stage(ProcessingStage.TRANSCRIPTION).attempts + 1, completedAtUtcMillis = System.currentTimeMillis()))
            repository.updateProcessing(session.id, transcribed)
            val original = timestamped.text
            val cleaned = cleaner.clean(original)
            if (policy.shouldDiscardTranscript(cleaned)) {
                repository.delete(session.id)
                return true
            }
            repository.saveTranscription(session.id, cleaned, original, timestamped.segments)
            val assembled = transcribed.withStage(ProcessingStage.ASSEMBLY,
                StageProgress(StageState.COMPLETE, 1, completedAtUtcMillis = System.currentTimeMillis()))
            repository.updateProcessing(session.id, assembled)
            try {
                val diarized = diarizer.diarize(audio, timestamped)
                repository.saveTranscription(session.id, cleaner.clean(diarized.text), diarized.text, diarized.segments)
                repository.updateProcessing(session.id, assembled.withStage(ProcessingStage.DIARIZATION,
                    StageProgress(StageState.COMPLETE, session.processing.stage(ProcessingStage.DIARIZATION).attempts + 1, completedAtUtcMillis = System.currentTimeMillis())))
            } catch (_: Throwable) {
                repository.updateProcessing(session.id, assembled.withStage(ProcessingStage.DIARIZATION,
                    StageProgress(StageState.FAILED, session.processing.stage(ProcessingStage.DIARIZATION).attempts + 1,
                        failure = ProcessingFailure(ProcessingStage.DIARIZATION, true, "Speaker identification could not be completed."))))
            }
            telemetry.record(TelemetryEvent.Processing(TelemetryEvent.Stage.TRANSCRIPTION, TelemetryEvent.Outcome.COMPLETED, durationBucket(audio.samples.size * 1_000L / audio.sampleRateHz)))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: NoSpeechDetectedException) {
            if (cleanupPolicy().minimumTranscriptWords > 0) {
                repository.delete(session.id)
                true
            } else {
                markTranscriptionFailed(
                    session,
                    ProcessingFailure(
                        stage = ProcessingStage.TRANSCRIPTION,
                        retryable = true,
                        userMessage = "No clear speech was found in this recording.",
                        debugCode = FAILURE_NO_SPEECH,
                    ),
                )
            }
        } catch (error: Throwable) {
            val failure = failureFor(error)
            runCatching {
                Log.e(TAG, "Local transcription failed [${failure.debugCode}] for session=$sessionId, audioBytes=${file.length()}", error)
            }
            markTranscriptionFailed(session, failure)
            telemetry.record(TelemetryEvent.Processing(TelemetryEvent.Stage.TRANSCRIPTION, TelemetryEvent.Outcome.FAILED, TelemetryEvent.DurationBucket.UNDER_30_SECONDS))
            telemetry.recordSanitizedFailure("background_transcription_failed")
            false
        }
    }

    private fun readPcm16(file: File): ShortArray {
        val byteCount = file.length()
        check(byteCount >= 2 && byteCount % 2 == 0L && byteCount <= Int.MAX_VALUE.toLong() * 2L) {
            "The saved conversation audio is invalid."
        }
        val samples = ShortArray((byteCount / 2L).toInt())
        BufferedInputStream(FileInputStream(file)).use { input ->
            val bytes = ByteArray(16 * 1024)
            var sampleIndex = 0
            while (sampleIndex < samples.size) {
                val read = input.read(bytes)
                check(read > 0 && read % 2 == 0) { "The saved conversation audio is invalid." }
                var index = 0
                while (index < read) {
                    samples[sampleIndex++] = ((bytes[index].toInt() and 0xFF) or (bytes[index + 1].toInt() shl 8)).toShort()
                    index += 2
                }
            }
        }
        return samples
    }

    private suspend fun markTranscriptionFailed(session: com.mobileobie.echo.model.SessionRecord, failure: ProcessingFailure): Boolean {
        repository.updateStatus(session.id, SessionStatus.TRANSCRIPTION_FAILED)
        val previous = session.processing.stage(ProcessingStage.TRANSCRIPTION)
        repository.updateProcessing(
            session.id,
            session.processing.withStage(
                ProcessingStage.TRANSCRIPTION,
                StageProgress(
                    state = StageState.FAILED,
                    attempts = previous.attempts + 1,
                    failure = failure,
                ),
            ),
        )
        return false
    }

    private fun failureFor(error: Throwable): ProcessingFailure = when (error) {
        is OutOfMemoryError -> ProcessingFailure(
            ProcessingStage.TRANSCRIPTION,
            retryable = true,
            userMessage = "This recording could not be processed because the device was low on memory. Try again after closing other apps.",
            debugCode = FAILURE_MEMORY_PRESSURE,
        )
        else -> ProcessingFailure(
            ProcessingStage.TRANSCRIPTION,
            retryable = true,
            userMessage = "Speech-to-text could not be completed. You can try again.",
            debugCode = FAILURE_TRANSCRIBER,
        )
    }

    private fun durationBucket(durationMillis: Long): TelemetryEvent.DurationBucket = when {
        durationMillis < 30_000 -> TelemetryEvent.DurationBucket.UNDER_30_SECONDS
        durationMillis < 120_000 -> TelemetryEvent.DurationBucket.UNDER_2_MINUTES
        else -> TelemetryEvent.DurationBucket.OVER_2_MINUTES
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val TAG = "HuhTranscription"
        const val FAILURE_SOURCE_AUDIO_MISSING = "source_audio_missing"
        const val FAILURE_NO_SPEECH = "no_speech_detected"
        const val FAILURE_MEMORY_PRESSURE = "memory_pressure"
        const val FAILURE_TRANSCRIBER = "transcriber_failed"
    }
}
