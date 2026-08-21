package com.echokeep.app.interpretation

import com.echokeep.app.model.ProcessedMessage

interface TranscriptInterpreter {
    suspend fun interpret(transcript: String): ProcessedMessage
    fun release() = Unit
}
