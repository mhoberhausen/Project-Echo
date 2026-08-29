package com.mobileobie.echo.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class SessionStatus(val displayName: String) {
    TRANSCRIBING("Turning speech into words…"),
    TRANSCRIPTION_FAILED("Transcription failed"),
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

enum class SessionSource(val displayName: String) {
    MANUAL("Listen Now"),
    ACTIVE_LISTENING("Active listening"),
    EXTERNAL_DEVICE("External device");

    companion object {
        fun fromStorage(value: String): SessionSource = when (value) {
            "XIAO" -> EXTERNAL_DEVICE
            else -> entries.firstOrNull { it.name == value } ?: MANUAL
        }
    }
}

data class ExternalDeviceSessionMetadata(
    val deviceId: String,
    val deviceName: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmwareVersion: String? = null,
    val protocolVersion: Int = 1,
    val transport: String,
    val streamId: String? = null,
    val gapCount: Int = 0,
    val reconnectCount: Int = 0,
    val receiverOverrunCount: Int = 0,
    val degraded: Boolean = false,
    val interrupted: Boolean = false,
)

/** A Whisper text interval with an optional speaker identity assigned after transcription. */
data class TranscriptSegment(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
    val speakerId: String? = null,
)

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
    val source: SessionSource = SessionSource.MANUAL,
    val audioPath: String? = null,
    val speechDurationMillis: Long = 0L,
    val speechSegmentCount: Int = 0,
    val longestInternalSilenceMillis: Long = 0L,
    val conversationEndSilenceMillis: Long = 0L,
    val externalDevice: ExternalDeviceSessionMetadata? = null,
    val transcriptSegments: List<TranscriptSegment> = emptyList(),
)

object SessionMetadata {
    private val defaultTitleFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")

    fun create(
        durationMillis: Long,
        transcript: String,
        originalTranscript: String,
        transcriptSegments: List<TranscriptSegment> = emptyList(),
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
            source = SessionSource.MANUAL,
            audioPath = null,
            transcriptSegments = transcriptSegments,
        )
    }

    fun createTranscribing(
        durationMillis: Long,
        transcriptionModel: TranscriptionModel,
        source: SessionSource,
        audioPath: String,
        speechDurationMillis: Long = 0L,
        speechSegmentCount: Int = 0,
        longestInternalSilenceMillis: Long = 0L,
        conversationEndSilenceMillis: Long = 0L,
        externalDevice: ExternalDeviceSessionMetadata? = null,
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
            status = SessionStatus.TRANSCRIBING,
            transcript = "",
            originalTranscript = "",
            processText = null,
            tags = emptyList(),
            transcriptionModel = transcriptionModel,
            source = source,
            audioPath = audioPath,
            speechDurationMillis = speechDurationMillis.coerceAtLeast(0),
            speechSegmentCount = speechSegmentCount.coerceAtLeast(0),
            longestInternalSilenceMillis = longestInternalSilenceMillis.coerceAtLeast(0),
            conversationEndSilenceMillis = conversationEndSilenceMillis.coerceAtLeast(0),
            externalDevice = externalDevice,
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
    append("\n\nWhat Was Said\n").append(transcript)
    processText?.takeIf { it.isNotBlank() }?.let { append("\n\nWhat I Got From It\n").append(it) }
    if (tags.isNotEmpty()) append("\n\nTags: ").append(tags.joinToString(", "))
}
