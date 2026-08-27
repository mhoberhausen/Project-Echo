package com.mobileobie.echo.transcription

import com.mobileobie.echo.model.TranscriptSegment

data class TimestampedTranscript(
    val segments: List<TranscriptSegment>,
) {
    val text: String = segments
        .map { it.text.trim() }
        .filter(String::isNotEmpty)
        .joinToString(" ")
        .trim()
}
