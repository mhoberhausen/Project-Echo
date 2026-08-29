package com.mobileobie.echo.transcription

class TranscriptCleaner {
    fun clean(transcript: String): String {
        if (transcript.isBlank()) return ""
        return transcript.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(::cleanLine)
            .filter(String::isNotEmpty)
            .joinToString("\n")
    }

    private fun cleanLine(line: String): String {
        val speakerMatch = SPEAKER_PREFIX.find(line)
        val prefix = speakerMatch?.value.orEmpty()
        var result = if (speakerMatch == null) line else line.removePrefix(prefix)

        // High-confidence standalone fillers only. Ambiguous words such as
        // "like", "so", and "well" are deliberately preserved.
        result = FILLERS.replace(result, " ")
        result = IMMEDIATE_REPETITION.replace(result) { match -> match.groupValues[1] }
        result = SPACE_BEFORE_PUNCTUATION.replace(result, "$1")
        result = MULTIPLE_SPACES.replace(result, " ").trim()
        result = EMPTY_PUNCTUATION.replace(result, "$1")
        result = result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (prefix.isEmpty()) result else "$prefix$result"
    }

    private companion object {
        val FILLERS = Regex(
            "(?i)(?:[,;:]\\s*)?(?<![\\p{L}\\p{N}])(?:um+|uh+|erm+|er+|hmm+)(?:[,.!?])?(?![\\p{L}\\p{N}])"
        )
        val IMMEDIATE_REPETITION = Regex("(?i)\\b([\\p{L}][\\p{L}'’-]*)[\\s,–—-]+\\1\\b")
        val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.!?;:])")
        val MULTIPLE_SPACES = Regex("[\\p{Zs}\\t\\x0B\\f]+")
        val EMPTY_PUNCTUATION = Regex("^[,;:]\\s*|([.!?])(?:\\s*[.!?])+")
        val SPEAKER_PREFIX = Regex("^[^:\\n]{1,40}:\\s+")
    }
}
