package com.huh.app.data

import com.huh.app.model.ProcessedMessage
import com.huh.app.model.SessionRecord
import com.huh.app.model.SessionStatus
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val sessions: StateFlow<List<SessionRecord>>

    suspend fun refresh()
    suspend fun create(session: SessionRecord)
    suspend fun updateStatus(id: String, status: SessionStatus)
    suspend fun saveProcessed(id: String, message: ProcessedMessage)
    suspend fun rename(id: String, title: String)
    suspend fun updateTranscript(id: String, transcript: String)
    suspend fun saveTranscription(id: String, transcript: String, originalTranscript: String)
    suspend fun delete(id: String)
}
