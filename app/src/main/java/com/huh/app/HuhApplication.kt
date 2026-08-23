package com.huh.app

import android.app.Application
import com.huh.app.data.SQLiteSessionRepository
import com.huh.app.transcription.SerializedTranscriber
import com.huh.app.transcription.TranscriptCleaner
import com.huh.app.transcription.TranscriptionQueue
import com.huh.app.transcription.TranscriptionProcessor
import com.huh.app.transcription.WhisperTranscriber
import com.huh.app.active.ActiveListeningSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HuhApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch { container.transcriptionQueue.recover() }
    }
}

class AppContainer(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val sessionRepository = SQLiteSessionRepository(application)
    val cleaner = TranscriptCleaner()
    val transcriber = SerializedTranscriber(WhisperTranscriber(application))
    val activeListeningSettings = ActiveListeningSettings(application)
    val transcriptionProcessor = TranscriptionProcessor(
        sessionRepository,
        transcriber,
        cleaner,
        activeListeningSettings::cleanupPolicy,
    )
    val transcriptionQueue = TranscriptionQueue(application, scope, sessionRepository)
}
