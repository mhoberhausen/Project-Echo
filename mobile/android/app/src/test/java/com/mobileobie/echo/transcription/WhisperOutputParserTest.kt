package com.mobileobie.echo.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperOutputParserTest {
    @Test
    fun parsesTimestampedSegmentsAndBuildsTranscriptText() {
        val result = WhisperOutputParser.parse(
            """{"segments":[{"start_ms":120,"end_ms":980,"text":" Hello"},{"start_ms":1000,"end_ms":1720,"text":" there."}]}"""
        )

        assertEquals("Hello there.", result.text)
        assertEquals(120, result.segments.first().startMillis)
        assertEquals(1_720, result.segments.last().endMillis)
        assertEquals(null, result.segments.first().speakerId)
    }
}
