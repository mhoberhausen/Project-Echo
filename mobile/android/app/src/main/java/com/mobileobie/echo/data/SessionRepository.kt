package com.mobileobie.echo.data

import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.TranscriptSegment
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val sessions: StateFlow<List<SessionRecord>>

    suspend fun refresh()
    suspend fun create(session: SessionRecord)
    suspend fun updateCapturedSession(session: SessionRecord)
    suspend fun updateStatus(id: String, status: SessionStatus)
    suspend fun saveProcessed(id: String, message: ProcessedMessage)
    suspend fun updateTitle(id: String, title: String)
    suspend fun updateTranscript(id: String, transcript: String)
    suspend fun renameSpeakers(id: String, names: Map<String, String>)
    suspend fun saveTranscription(
        id: String,
        transcript: String,
        originalTranscript: String,
        segments: List<TranscriptSegment>,
    )
    suspend fun delete(id: String)
}
