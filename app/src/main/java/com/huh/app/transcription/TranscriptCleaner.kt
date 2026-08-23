package com.huh.app.transcription

class TranscriptCleaner {
    fun clean(transcript: String): String {
        if (transcript.isBlank()) return ""
        var result = transcript.trim()

        // High-confidence standalone fillers only. Ambiguous words such as
        // "like", "so", and "well" are deliberately preserved.
        result = FILLERS.replace(result, " ")
        result = IMMEDIATE_REPETITION.replace(result) { match -> match.groupValues[1] }
        result = SPACE_BEFORE_PUNCTUATION.replace(result, "$1")
        result = MULTIPLE_SPACES.replace(result, " ").trim()
        result = EMPTY_PUNCTUATION.replace(result, "$1")
        return result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private companion object {
        val FILLERS = Regex(
            "(?i)(?:[,;:]\\s*)?(?<![\\p{L}\\p{N}])(?:um+|uh+|erm+|er+|hmm+)(?:[,.!?])?(?![\\p{L}\\p{N}])"
        )
        val IMMEDIATE_REPETITION = Regex("(?i)\\b([\\p{L}][\\p{L}'’-]*)[\\s,–—-]+\\1\\b")
        val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.!?;:])")
        val MULTIPLE_SPACES = Regex("\\s+")
        val EMPTY_PUNCTUATION = Regex("^[,;:]\\s*|([.!?])(?:\\s*[.!?])+")
    }
}
