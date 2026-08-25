package com.mobileobie.echo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobileobie.echo.model.ActionItem
import com.mobileobie.echo.model.MessageIntent
import com.mobileobie.echo.model.ExternalDeviceSessionMetadata
import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.model.SessionMetadata
import com.mobileobie.echo.model.SessionSource
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.TranscriptionModel
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
        assertEquals(emptyList<com.mobileobie.echo.model.SessionRecord>(), reloaded.sessions.value)
    }

    @Test
    fun persistsUniversalExternalDeviceMetadata() = runBlocking {
        val session = SessionMetadata.createTranscribing(
            durationMillis = 4_000,
            transcriptionModel = TranscriptionModel.FAST,
            source = SessionSource.EXTERNAL_DEVICE,
            audioPath = "external.pcm",
            externalDevice = ExternalDeviceSessionMetadata(
                deviceId = "device-123",
                deviceName = "Kitchen recorder",
                manufacturer = "Acme",
                model = "Recorder One",
                firmwareVersion = "1.2.3",
                transport = "Local Wi-Fi",
                streamId = "stream-456",
                gapCount = 2,
                degraded = true,
            ),
        )

        repository.create(session)
        val restored = repository.sessions.value.single()

        assertEquals(SessionSource.EXTERNAL_DEVICE, restored.source)
        assertEquals("device-123", restored.externalDevice?.deviceId)
        assertEquals("Recorder One", restored.externalDevice?.model)
        assertEquals(2, restored.externalDevice?.gapCount)
        assertEquals(true, restored.externalDevice?.degraded)
    }
}
