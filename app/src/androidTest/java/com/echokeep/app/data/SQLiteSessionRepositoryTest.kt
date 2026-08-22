package com.echokeep.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.echokeep.app.model.ActionItem
import com.echokeep.app.model.MessageIntent
import com.echokeep.app.model.ProcessedMessage
import com.echokeep.app.model.SessionMetadata
import com.echokeep.app.model.SessionStatus
import com.echokeep.app.model.TranscriptionModel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SQLiteSessionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "session-test-${UUID.randomUUID()}.db"
    private lateinit var repository: SQLiteSessionRepository

    @Before
    fun setUp() {
        repository = SQLiteSessionRepository(context, databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun persistsLifecycleUpdatesAndDeletesSession() = runBlocking {
        val session = SessionMetadata.create(
            durationMillis = 12_500,
            transcript = "Transcript",
            originalTranscript = "Um transcript",
            transcriptionModel = TranscriptionModel.ACCURATE,
            nowUtcMillis = 123_456,
            id = "saved-session",
        )

        repository.create(session)
        repository.updateStatus(session.id, SessionStatus.QUEUED)
        repository.updateStatus(session.id, SessionStatus.PROCESSING)
        repository.saveProcessed(
            session.id,
            ProcessedMessage(
                summary = "Summary",
                intent = MessageIntent.NOTE,
                keyPoints = listOf("Point"),
                actionItems = listOf(ActionItem("Follow up", null)),
                tags = listOf("Work", "work", "Follow-up"),
            ),
        )

        val processed = repository.sessions.value.single()
        assertEquals(SessionStatus.PROCESSED, processed.status)
        assertEquals(listOf("Work", "Follow-up"), processed.tags)
        assertEquals("Summary", processed.processText?.substringBefore("\n"))

        repository.rename(session.id, "Renamed")
        assertEquals("Renamed", repository.sessions.value.single().title)

        repository.updateTranscript(session.id, "Edited transcript")
        val edited = repository.sessions.value.single()
        assertEquals(SessionStatus.TRANSCRIBED, edited.status)
        assertEquals("Edited transcript", edited.transcript)
        assertNull(edited.processText)
        assertEquals(emptyList<String>(), edited.tags)

        val reloaded = SQLiteSessionRepository(context, databaseName)
        reloaded.refresh()
        assertEquals("Edited transcript", reloaded.sessions.value.single().transcript)

        reloaded.delete(session.id)
        assertEquals(emptyList<com.echokeep.app.model.SessionRecord>(), reloaded.sessions.value)
    }
}
