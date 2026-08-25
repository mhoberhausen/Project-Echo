package com.mobileobie.echo

import android.app.Application
import com.mobileobie.echo.data.SQLiteSessionRepository
import com.mobileobie.echo.transcription.SerializedTranscriber
import com.mobileobie.echo.transcription.TranscriptCleaner
import com.mobileobie.echo.transcription.TranscriptionQueue
import com.mobileobie.echo.transcription.TranscriptionProcessor
import com.mobileobie.echo.transcription.WhisperTranscriber
import com.mobileobie.echo.active.ActiveListeningSettings
import com.mobileobie.echo.external.ExternalDeviceSettings
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
    val externalDeviceSettings = ExternalDeviceSettings(application)
    val transcriptionProcessor = TranscriptionProcessor(
        sessionRepository,
        transcriber,
        cleaner,
        activeListeningSettings::cleanupPolicy,
    )
    val transcriptionQueue = TranscriptionQueue(application, scope, sessionRepository)
}
