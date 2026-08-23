package com.huh.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.huh.app.audio.AudioRecorder
import com.huh.app.data.SessionRepository
import com.huh.app.interpretation.TranscriptInterpreter
import com.huh.app.transcription.Transcriber
import com.huh.app.transcription.TranscriptCleaner

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
