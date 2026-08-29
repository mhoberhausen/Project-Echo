package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.data.SessionRepository
import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.model.SessionMetadata
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionSource
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.model.TranscriptSegment
import com.mobileobie.echo.active.AutomaticCaptureCleanupPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Processor contract tests; SerializedTranscriber supplies the queue's single-model invariant. */
class TranscriptionProcessorTest {
    @Test
    fun jobsAreProcessedSeriallyAndSaved() = runBlocking {
        val repository = FakeRepository()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val transcriber = object : Transcriber {
            override suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel): TimestampedTranscript {
                val count = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                delay(75)
                active.decrementAndGet()
                return timestamped("Um hello there")
            }
        }
        val processor = TranscriptionProcessor(
            repository,
            SerializedTranscriber(transcriber),
            PassthroughSpeakerDiarizer,
            TranscriptCleaner(),
            { AutomaticCaptureCleanupPolicy(0) },
        )
        val sessions = (1..2).map { index -> queuedSession("queue-$index") }
        sessions.forEach { repository.create(it) }

        sessions.map { session -> async { processor.process(session.id) } }.awaitAll()

        assertEquals(1, maximum.get())
        assertEquals(2, repository.sessions.value.count { it.status == SessionStatus.TRANSCRIBED })
        assertEquals(listOf("Hello there", "Hello there"), repository.sessions.value.map { it.transcript })
        assertEquals(true, sessions.all { !File(requireNotNull(it.audioPath)).exists() })
    }

    @Test
    fun transcriptBelowWordThresholdDeletesSessionAndAudio() = runBlocking {
        val repository = FakeRepository()
        val session = queuedSession("short-transcript").copy(durationMillis = 6_000)
        repository.create(session)
        val processor = TranscriptionProcessor(
            repository,
            object : Transcriber {
                override suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel) =
                    timestamped("Two words")
            },
            PassthroughSpeakerDiarizer,
            TranscriptCleaner(),
            { AutomaticCaptureCleanupPolicy(3) },
        )

        processor.process(session.id)

        assertEquals(emptyList<SessionRecord>(), repository.sessions.value)
        assertEquals(false, File(requireNotNull(session.audioPath)).exists())
    }

    @Test
    fun diarizationRunsAfterTranscriptionAndSegmentsAreSaved() = runBlocking {
        val repository = FakeRepository()
        val session = queuedSession("diarization-order")
        repository.create(session)
        var transcribed = false
        val processor = TranscriptionProcessor(
            repository,
            object : Transcriber {
                override suspend fun transcribe(
                    audio: RecordedAudio,
                    model: TranscriptionModel,
                ): TimestampedTranscript {
                    transcribed = true
                    return timestamped("Hello there")
                }
            },
            SpeakerDiarizer { _, transcript ->
                check(transcribed)
                transcript.copy(segments = transcript.segments.map { it.copy(speakerId = "speaker-1") })
            },
            TranscriptCleaner(),
            { AutomaticCaptureCleanupPolicy(0) },
        )

        processor.process(session.id)

        assertEquals("speaker-1", repository.sessions.value.single().transcriptSegments.single().speakerId)
    }

    @Test
    fun precomputedChunksSkipWhisperButStillRunFullAudioDiarization() = runBlocking {
        val repository = FakeRepository()
        val segment = TranscriptSegment(250, 750, "Already transcribed")
        val session = queuedSession("incremental").copy(transcriptSegments = listOf(segment))
        repository.create(session)
        var diarizedAudioSamples = 0
        val processor = TranscriptionProcessor(
            repository,
            object : Transcriber {
                override suspend fun transcribe(
                    audio: RecordedAudio,
                    model: TranscriptionModel,
                ): TimestampedTranscript = error("Whisper should not run twice")
            },
            SpeakerDiarizer { audio, transcript ->
                diarizedAudioSamples = audio.samples.size
                transcript.copy(
                    segments = transcript.segments.map { it.copy(speakerId = "speaker-1") },
                )
            },
            TranscriptCleaner(),
            { AutomaticCaptureCleanupPolicy(0) },
        )

        assertEquals(true, processor.process(session.id))

        val saved = repository.sessions.value.single()
        assertEquals(320, diarizedAudioSamples)
        assertEquals("Speaker 1: Already transcribed", saved.transcript)
        assertEquals("speaker-1", saved.transcriptSegments.single().speakerId)
    }

    @Test
    fun failedTranscriptionIsRetainedForExplicitRetry() = runBlocking {
        val repository = FakeRepository()
        val session = queuedSession("failed-transcription")
        repository.create(session)
        val processor = TranscriptionProcessor(
            repository,
            object : Transcriber {
                override suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel): TimestampedTranscript {
                    error("Native transcription failed")
                }
            },
            PassthroughSpeakerDiarizer,
            TranscriptCleaner(),
            { AutomaticCaptureCleanupPolicy(0) },
        )

        assertEquals(false, processor.process(session.id))
        assertEquals(SessionStatus.TRANSCRIPTION_FAILED, repository.sessions.value.single().status)
        assertEquals(true, File(requireNotNull(session.audioPath)).exists())
        repository.delete(session.id)
    }

    private fun queuedSession(id: String): SessionRecord {
        val file = File.createTempFile(id, ".pcm")
        file.writeBytes(ByteArray(640) { if (it % 2 == 0) 1 else 0 })
        return SessionMetadata.createTranscribing(
            durationMillis = 20,
            transcriptionModel = TranscriptionModel.FAST,
            source = SessionSource.ACTIVE_LISTENING,
            audioPath = file.absolutePath,
            id = id,
        )
    }
}

private fun timestamped(text: String) = TimestampedTranscript(
    listOf(TranscriptSegment(startMillis = 100, endMillis = 900, text = text))
)

private class FakeRepository : SessionRepository {
    private val mutableSessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    override val sessions: StateFlow<List<SessionRecord>> = mutableSessions

    override suspend fun refresh() = Unit
    override suspend fun create(session: SessionRecord) {
        mutableSessions.value = mutableSessions.value + session
    }
    override suspend fun updateStatus(id: String, status: SessionStatus) {
        mutableSessions.value = mutableSessions.value.map { if (it.id == id) it.copy(status = status) else it }
    }
    override suspend fun saveTranscription(
        id: String,
        transcript: String,
        originalTranscript: String,
        segments: List<TranscriptSegment>,
    ) {
        mutableSessions.value = mutableSessions.value.map {
            if (it.id == id) it.copy(
                status = SessionStatus.TRANSCRIBED,
                transcript = transcript,
                originalTranscript = originalTranscript,
                audioPath = null,
                transcriptSegments = segments,
            ) else it
        }
    }
    override suspend fun saveProcessed(id: String, message: ProcessedMessage) = Unit
    override suspend fun updateTitle(id: String, title: String) = Unit
    override suspend fun updateTranscript(id: String, transcript: String) = Unit
    override suspend fun renameSpeakers(id: String, names: Map<String, String>) = Unit
    override suspend fun delete(id: String) {
        mutableSessions.value.firstOrNull { it.id == id }?.audioPath?.let { File(it).delete() }
        mutableSessions.value = mutableSessions.value.filterNot { it.id == id }
    }
}
