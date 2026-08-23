package com.huh.app.model

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

    @Test
    fun activeSessionRetainsConversationTimingMetadata() {
        val session = SessionMetadata.createTranscribing(
            durationMillis = 105_422,
            transcriptionModel = TranscriptionModel.ACCURATE,
            source = SessionSource.ACTIVE_LISTENING,
            audioPath = "conversation.pcm",
            speechDurationMillis = 48_213,
            speechSegmentCount = 27,
            longestInternalSilenceMillis = 7_834,
            conversationEndSilenceMillis = 15_000,
            id = "active-session",
        )

        assertEquals(105_422, session.durationMillis)
        assertEquals(48_213, session.speechDurationMillis)
        assertEquals(27, session.speechSegmentCount)
        assertEquals(7_834, session.longestInternalSilenceMillis)
        assertEquals(15_000, session.conversationEndSilenceMillis)
    }
}
