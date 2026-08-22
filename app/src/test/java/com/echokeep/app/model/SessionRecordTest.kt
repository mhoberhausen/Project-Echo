package com.echokeep.app.model

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRecordTest {
    @Test
    fun createsUtcBackedSessionWithLocalDisplayTitle() {
        val session = SessionMetadata.create(
            durationMillis = 65_432,
            transcript = "Concise transcript",
            originalTranscript = "Um, concise transcript",
            transcriptionModel = TranscriptionModel.FAST,
            nowUtcMillis = 1_704_067_200_000,
            zoneId = ZoneId.of("America/New_York"),
            id = "session-1",
        )

        assertEquals(1_704_067_200_000, session.createdAtUtcMillis)
        assertEquals("Session · Dec 31, 2023 · 7:00 PM", session.title)
        assertEquals(SessionStatus.TRANSCRIBED, session.status)
        assertEquals("1:05", SessionMetadata.displayDuration(session.durationMillis))
    }

    @Test
    fun formatsProcessedMessageForPersistenceAndSharing() {
        val message = ProcessedMessage(
            summary = "Book the appointment.",
            intent = MessageIntent.TASK,
            keyPoints = listOf("Call the office"),
            actionItems = listOf(ActionItem("Call tomorrow", "tomorrow")),
            tags = listOf("health"),
        )

        assertEquals(
            "Book the appointment.\n\nIntent: Task\n\nKey points\n• Call the office\n\nAction items\n• Call tomorrow — tomorrow",
            ProcessedMessageText.format(message),
        )
    }
}
