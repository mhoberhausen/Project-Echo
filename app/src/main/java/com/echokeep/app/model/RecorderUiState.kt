package com.echokeep.app.model

enum class RecordingPhase { IDLE, RECORDING, PROCESSING, COMPLETE, ERROR }

data class RecorderUiState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val elapsedSeconds: Long = 0,
    val cleanedTranscript: String = "",
    val originalTranscript: String = "",
    val errorMessage: String? = null,
)
