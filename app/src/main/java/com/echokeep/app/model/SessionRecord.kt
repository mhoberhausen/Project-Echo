package com.echokeep.app.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class SessionStatus(val displayName: String) {
    TRANSCRIBED("Pending"),
    QUEUED("Queued"),
    PROCESSING("Processing"),
    PROCESSED("Processed"),
    FAILED("Processing failed");

    val isPending: Boolean get() = this != PROCESSED

    companion object {
        fun fromStorage(value: String): SessionStatus =
            entries.firstOrNull { it.name == value } ?: FAILED
    }
}

data class SessionRecord(
    val id: String,
    val createdAtUtcMillis: Long,
    val updatedAtUtcMillis: Long,
    val durationMillis: Long,
    val title: String,
    val status: SessionStatus,
    val transcript: String,
    val originalTranscript: String,
    val processText: String?,
    val tags: List<String>,
    val transcriptionModel: TranscriptionModel,
)

object SessionMetadata {
    private val defaultTitleFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")

    fun create(
        durationMillis: Long,
        transcript: String,
        originalTranscript: String,
        transcriptionModel: TranscriptionModel,
        nowUtcMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        id: String = UUID.randomUUID().toString(),
    ): SessionRecord {
        val localDate = Instant.ofEpochMilli(nowUtcMillis).atZone(zoneId)
        return SessionRecord(
            id = id,
            createdAtUtcMillis = nowUtcMillis,
            updatedAtUtcMillis = nowUtcMillis,
            durationMillis = durationMillis.coerceAtLeast(0),
            title = "Session · ${defaultTitleFormatter.format(localDate)}",
            status = SessionStatus.TRANSCRIBED,
            transcript = transcript,
            originalTranscript = originalTranscript,
            processText = null,
            tags = emptyList(),
            transcriptionModel = transcriptionModel,
        )
    }

    fun displayDate(utcMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        displayDateFormatter.format(Instant.ofEpochMilli(utcMillis).atZone(zoneId))

    fun displayDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis.coerceAtLeast(0) / 1_000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

object ProcessedMessageText {
    fun format(message: ProcessedMessage): String = buildString {
        append(message.summary)
        append("\n\nIntent: ").append(message.intent.displayName)
        if (message.keyPoints.isNotEmpty()) {
            append("\n\nKey points")
            message.keyPoints.forEach { append("\n• ").append(it) }
        }
        if (message.actionItems.isNotEmpty()) {
            append("\n\nAction items")
            message.actionItems.forEach { item ->
                append("\n• ").append(item.text)
                item.dueDate?.let { append(" — ").append(it) }
            }
        }
    }
}

fun SessionRecord.shareText(): String = buildString {
    append(title)
    append("\n").append(SessionMetadata.displayDate(createdAtUtcMillis))
    append(" · ").append(SessionMetadata.displayDuration(durationMillis))
    append(" · ").append(status.displayName)
    append("\n\nTranscript\n").append(transcript)
    processText?.takeIf { it.isNotBlank() }?.let { append("\n\nProcessed\n").append(it) }
    if (tags.isNotEmpty()) append("\n\nTags: ").append(tags.joinToString(", "))
}
