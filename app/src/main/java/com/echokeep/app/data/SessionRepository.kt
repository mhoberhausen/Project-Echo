package com.echokeep.app.data

import com.echokeep.app.model.ProcessedMessage
import com.echokeep.app.model.SessionRecord
import com.echokeep.app.model.SessionStatus
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val sessions: StateFlow<List<SessionRecord>>

    suspend fun refresh()
    suspend fun create(session: SessionRecord)
    suspend fun updateStatus(id: String, status: SessionStatus)
    suspend fun saveProcessed(id: String, message: ProcessedMessage)
    suspend fun rename(id: String, title: String)
    suspend fun updateTranscript(id: String, transcript: String)
    suspend fun delete(id: String)
}
