package com.mobileobie.echo.transcription

import com.mobileobie.echo.model.TranscriptSegment

data class TimestampedTranscript(
    val segments: List<TranscriptSegment>,
) {
    val text: String = renderText(segments)
}

private fun renderText(segments: List<TranscriptSegment>): String {
    val spoken = segments.filter { it.text.isNotBlank() }
    if (spoken.none { it.speakerId != null }) {
        return spoken.joinToString(" ") { it.text.trim() }.trim()
    }

    return buildList {
        var currentSpeaker: String? = null
        var currentText = StringBuilder()
        spoken.forEach { segment ->
            val speaker = segment.speakerId ?: UNKNOWN_SPEAKER
            if (currentSpeaker != speaker) {
                if (currentSpeaker != null) add("${SpeakerLabels.displayName(currentSpeaker!!)}: $currentText")
                currentSpeaker = speaker
                currentText = StringBuilder(segment.text.trim())
            } else {
                currentText.append(' ').append(segment.text.trim())
            }
        }
        if (currentSpeaker != null) add("${SpeakerLabels.displayName(currentSpeaker!!)}: $currentText")
    }.joinToString("\n")
}

private const val UNKNOWN_SPEAKER = "Unknown speaker"
