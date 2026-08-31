package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.audio.RecordedAudioChunk
import com.mobileobie.echo.model.TranscriptSegment
import com.mobileobie.echo.model.TranscriptionModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementalTranscriptionWorkerTest {
    @Test
    fun seriallyMergesChunksAtTheirOriginalTimelineOffsets() = runBlocking {
        var call = 0
        val worker = IncrementalTranscriptionWorker(
            scope = this,
            transcriber = object : Transcriber {
                override suspend fun transcribe(
                    audio: RecordedAudio,
                    model: TranscriptionModel,
                ): TimestampedTranscript = TimestampedTranscript(
                    listOf(TranscriptSegment(10, 90, "chunk ${++call}")),
                )
            },
            model = TranscriptionModel.FAST,
        )

        worker.offer(RecordedAudioChunk(RecordedAudio(shortArrayOf(1), 16_000), 0))
        worker.offer(RecordedAudioChunk(RecordedAudio(shortArrayOf(2), 16_000), 2_000))
        val result = worker.finish()!!

        assertEquals(listOf("chunk 1", "chunk 2"), result.segments.map { it.text })
        assertEquals(listOf(10L, 2_010L), result.segments.map { it.startMillis })
        assertEquals(listOf(90L, 2_090L), result.segments.map { it.endMillis })
    }

    @Test
    fun noSpeechFromAnyChunkRequiresFullAudioFallback() = runBlocking {
        val worker = IncrementalTranscriptionWorker(
            scope = this,
            transcriber = object : Transcriber {
                override suspend fun transcribe(
                    audio: RecordedAudio,
                    model: TranscriptionModel,
                ): TimestampedTranscript = throw NoSpeechDetectedException()
            },
            model = TranscriptionModel.FAST,
        )

        worker.offer(RecordedAudioChunk(RecordedAudio(shortArrayOf(1), 16_000), 0))

        assertEquals(null, worker.finish())
    }
}
