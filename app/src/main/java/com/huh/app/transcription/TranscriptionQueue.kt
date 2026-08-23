package com.huh.app.transcription

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.huh.app.data.SessionRepository
import com.huh.app.model.SessionRecord
import com.huh.app.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Durable, serial scheduler for local Whisper work. */
class TranscriptionQueue(
    context: Context,
    scope: CoroutineScope,
    private val repository: SessionRepository,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        scope.launch {
            repository.sessions.collect { sessions ->
                _pendingCount.value = sessions.count { it.status == SessionStatus.TRANSCRIBING }
            }
        }
    }

    fun enqueue(session: SessionRecord) {
        if (session.audioPath.isNullOrBlank()) return
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(Data.Builder().putString(TranscriptionWorker.KEY_SESSION_ID, session.id).build())
            .addTag(TAG)
            .addTag(sessionTag(session.id))
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_QUEUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    suspend fun recover() {
        repository.refresh()
        repository.sessions.value
            .filter { it.status == SessionStatus.TRANSCRIBING && !it.audioPath.isNullOrBlank() }
            .filter { session ->
                workManager.getWorkInfosByTag(sessionTag(session.id)).get()
                    .none { !it.state.isFinished }
            }
            .forEach(::enqueue)
    }

    companion object {
        const val UNIQUE_QUEUE_NAME = "local_whisper_transcription_queue"
        const val TAG = "local_whisper_transcription"
        private const val SESSION_TAG_PREFIX = "local_whisper_session_"
        private fun sessionTag(sessionId: String) = "$SESSION_TAG_PREFIX$sessionId"
    }
}
