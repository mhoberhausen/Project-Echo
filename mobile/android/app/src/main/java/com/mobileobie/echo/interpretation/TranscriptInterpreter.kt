package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.ProcessedMessage

interface TranscriptInterpreter {
    suspend fun interpret(transcript: String): ProcessedMessage
    fun release() = Unit
}
