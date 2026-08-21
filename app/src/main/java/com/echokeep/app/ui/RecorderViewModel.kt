package com.echokeep.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echokeep.app.audio.AudioRecorder
import com.echokeep.app.interpretation.TranscriptInterpreter
import com.echokeep.app.model.RecorderUiState
import com.echokeep.app.model.RecordingPhase
import com.echokeep.app.model.TranscriptionModel
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
    private val interpreter: TranscriptInterpreter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null

    fun startRecording() {
        if (_uiState.value.phase == RecordingPhase.RECORDING) return
        viewModelScope.launch {
            runCatching { recorder.start() }
                .onSuccess {
                    _uiState.update {
                        RecorderUiState(
                            phase = RecordingPhase.RECORDING,
                            selectedModel = it.selectedModel,
                        )
                    }
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
                val model = _uiState.value.selectedModel
                val original = transcriber.transcribe(audio, model)
                original to cleaner.clean(original)
            }.onSuccess { (original, cleaned) ->
                _uiState.value = RecorderUiState(
                    phase = RecordingPhase.COMPLETE,
                    originalTranscript = original,
                    cleanedTranscript = cleaned,
                    selectedModel = _uiState.value.selectedModel,
                )
            }.onFailure(::showError)
        }
    }

    fun clear() {
        if (_uiState.value.phase != RecordingPhase.PROCESSING) {
            _uiState.update { RecorderUiState(selectedModel = it.selectedModel) }
        }
    }

    fun processTranscript() {
        if (_uiState.value.phase != RecordingPhase.COMPLETE) return
        _uiState.update { it.copy(phase = RecordingPhase.INTERPRETING, errorMessage = null) }
        viewModelScope.launch {
            runCatching { interpreter.interpret(_uiState.value.cleanedTranscript) }
                .onSuccess { result ->
                    _uiState.update { it.copy(phase = RecordingPhase.PROCESSED, processedMessage = result) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            phase = RecordingPhase.COMPLETE,
                            errorMessage = error.message ?: "Gemma could not process this transcript.",
                        )
                    }
                }
        }
    }

    fun selectModel(model: TranscriptionModel) {
        if (_uiState.value.phase !in setOf(
                RecordingPhase.RECORDING,
                RecordingPhase.PROCESSING,
                RecordingPhase.INTERPRETING,
            )
        ) {
            _uiState.update { it.copy(selectedModel = model) }
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
        interpreter.release()
        super.onCleared()
    }
}
