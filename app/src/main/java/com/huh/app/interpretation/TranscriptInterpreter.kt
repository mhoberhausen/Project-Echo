package com.huh.app.interpretation

import com.huh.app.model.ProcessedMessage

interface TranscriptInterpreter {
    suspend fun interpret(transcript: String): ProcessedMessage
    fun release() = Unit
}
