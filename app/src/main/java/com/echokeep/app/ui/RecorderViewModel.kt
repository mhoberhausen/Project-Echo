package com.echokeep.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echokeep.app.audio.AudioRecorder
import com.echokeep.app.model.RecorderUiState
import com.echokeep.app.model.RecordingPhase
import com.echokeep.app.transcription.Transcriber
import com.echokeep.app.transcription.TranscriptCleaner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecorderViewModel(
    private val recorder: AudioRecorder,
    private val transcriber: Transcriber,
    private val cleaner: TranscriptCleaner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null

    fun startRecording() {
        if (_uiState.value.phase == RecordingPhase.RECORDING) return
        viewModelScope.launch {
            runCatching { recorder.start() }
                .onSuccess {
                    _uiState.value = RecorderUiState(phase = RecordingPhase.RECORDING)
                    startTimer()
                }
                .onFailure(::showError)
        }
    }

    fun stopRecording() {
        if (_uiState.value.phase != RecordingPhase.RECORDING) return
        timerJob?.cancel()
        _uiState.update { it.copy(phase = RecordingPhase.PROCESSING) }
        viewModelScope.launch {
            runCatching {
                val audio = recorder.stop()
                val original = transcriber.transcribe(audio)
                original to cleaner.clean(original)
            }.onSuccess { (original, cleaned) ->
                _uiState.value = RecorderUiState(
                    phase = RecordingPhase.COMPLETE,
                    originalTranscript = original,
                    cleanedTranscript = cleaned,
                )
            }.onFailure(::showError)
        }
    }

    fun clear() {
        if (_uiState.value.phase != RecordingPhase.PROCESSING) {
            _uiState.value = RecorderUiState()
        }
    }

    fun permissionDenied() {
        showError(IllegalStateException("Microphone permission is required to record."))
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(
                phase = RecordingPhase.ERROR,
                errorMessage = error.message ?: "Something went wrong. Please try again.",
            )
        }
    }

    override fun onCleared() {
        recorder.release()
        super.onCleared()
    }
}
