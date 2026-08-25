package com.mobileobie.echo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobileobie.echo.audio.AudioRecorder
import com.mobileobie.echo.data.SessionRepository
import com.mobileobie.echo.interpretation.TranscriptInterpreter
import com.mobileobie.echo.transcription.Transcriber
import com.mobileobie.echo.transcription.TranscriptCleaner

class RecorderViewModelFactory(
    private val recorder: AudioRecorder,
    private val transcriber: Transcriber,
    private val cleaner: TranscriptCleaner,
    private val interpreter: TranscriptInterpreter,
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RecorderViewModel::class.java))
        return RecorderViewModel(recorder, transcriber, cleaner, interpreter, sessionRepository) as T
    }
}
