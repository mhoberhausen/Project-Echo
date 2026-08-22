package com.echokeep.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echokeep.app.audio.AudioRecorder
import com.echokeep.app.data.SessionRepository
import com.echokeep.app.interpretation.TranscriptInterpreter
import com.echokeep.app.transcription.Transcriber
import com.echokeep.app.transcription.TranscriptCleaner

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
