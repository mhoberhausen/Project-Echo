package com.mobileobie.echo.model

enum class RecordingPhase { IDLE, RECORDING, PROCESSING, COMPLETE, INTERPRETING, PROCESSED, ERROR }

enum class MessageIntent(val displayName: String) {
    NOTE("Note"), IDEA("Idea"), REMINDER("Reminder"), TASK("Task"),
    CONVERSATION("Conversation"), QUESTION("Question"), UNKNOWN("Unknown");

    companion object {
        fun fromWireValue(value: String): MessageIntent =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

data class ActionItem(val text: String, val dueDate: String?)

data class ProcessedMessage(
    val summary: String,
    val intent: MessageIntent,
    val keyPoints: List<String>,
    val actionItems: List<ActionItem>,
    val tags: List<String> = emptyList(),
)

data class RecorderUiState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val elapsedSeconds: Long = 0,
    val cleanedTranscript: String = "",
    val originalTranscript: String = "",
    val errorMessage: String? = null,
    val selectedModel: TranscriptionModel = TranscriptionModel.ACCURATE,
    val processedMessage: ProcessedMessage? = null,
    val sessionId: String? = null,
    val speakerIds: List<String> = emptyList(),
)
