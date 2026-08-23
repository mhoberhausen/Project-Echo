package com.huh.app.active

data class AutomaticCaptureCleanupPolicy(
    val minimumTranscriptWords: Int,
) {
    init {
        require(minimumTranscriptWords >= 0)
    }

    fun shouldDiscardTranscript(transcript: String): Boolean =
        wordCount(transcript) < minimumTranscriptWords

    fun wordCount(transcript: String): Int = WORD.findAll(transcript).count()

    private companion object {
        val WORD = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
    }
}
