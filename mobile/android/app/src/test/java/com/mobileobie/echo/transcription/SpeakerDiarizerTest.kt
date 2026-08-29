package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerDiarizerTest {
    @Test
    fun assignsSpeakerWithGreatestOverlapAndPreservesUnmatchedSegments() {
        val transcript = TimestampedTranscript(
            listOf(
                segment(0, 1_000, "Hello"),
                segment(1_000, 2_000, "Hi"),
                segment(3_000, 4_000, "Goodbye"),
            )
        )

        val result = SpeakerTurnAligner.assign(
            transcript,
            listOf(
                SpeakerTurn(0, 800, "speaker-1"),
                SpeakerTurn(700, 1_700, "speaker-2"),
            ),
        )

        assertEquals(listOf("speaker-1", "speaker-2", null), result.segments.map { it.speakerId })
    }

    @Test
    fun replaceableEngineRunsBeforeTimestampAlignment() = runBlocking {
        val audio = RecordedAudio(shortArrayOf(1, 2), 16_000)
        var receivedAudio: RecordedAudio? = null
        val diarizer = TimestampAligningSpeakerDiarizer { input ->
            receivedAudio = input
            listOf(SpeakerTurn(0, 1_000, "guest"))
        }

        val result = diarizer.diarize(audio, TimestampedTranscript(listOf(segment(0, 500, "Hello"))))

        assertEquals(audio, receivedAudio)
        assertEquals("guest", result.segments.single().speakerId)
    }

    @Test
    fun speakerLabelsAreGroupedForDownstreamInference() {
        val transcript = TimestampedTranscript(
            listOf(
                segment(0, 500, "Hello", "speaker-1"),
                segment(500, 1_000, "there", "speaker-1"),
                segment(1_000, 1_500, "Hi", "speaker-2"),
                segment(1_500, 2_000, "again", "speaker-1"),
            )
        )

        assertEquals(
            "Speaker 1: Hello there\nSpeaker 2: Hi\nSpeaker 1: again",
            transcript.text,
        )
    }

    @Test
    fun transcriptWithoutSpeakerLabelsKeepsExistingFlatText() {
        val transcript = TimestampedTranscript(
            listOf(segment(0, 500, " Hello "), segment(500, 1_000, "there"))
        )

        assertEquals("Hello there", transcript.text)
    }

    private fun segment(
        startMillis: Long,
        endMillis: Long,
        text: String,
        speakerId: String? = null,
    ) = TranscriptSegment(startMillis, endMillis, text, speakerId)
}
