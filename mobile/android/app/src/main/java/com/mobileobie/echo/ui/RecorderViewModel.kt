package com.mobileobie.echo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileobie.echo.audio.AudioRecorder
import com.mobileobie.echo.data.SessionRepository
import com.mobileobie.echo.interpretation.TranscriptInterpreter
import com.mobileobie.echo.model.RecorderUiState
import com.mobileobie.echo.model.RecordingPhase
import com.mobileobie.echo.model.SessionMetadata
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.transcription.Transcriber
import com.mobileobie.echo.transcription.TranscriptCleaner
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
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()
    val sessions: StateFlow<List<SessionRecord>> = sessionRepository.sessions
    private var timerJob: Job? = null
    private var processingJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { sessionRepository.refresh() }.onFailure(::showError)
        }
    }

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
                val cleaned = cleaner.clean(original)
                val durationMillis = audio.samples.size * 1_000L / audio.sampleRateHz
                val session = SessionMetadata.create(
                    durationMillis = durationMillis,
                    transcript = cleaned,
                    originalTranscript = original,
                    transcriptionModel = model,
                )
                sessionRepository.create(session)
                session
            }.onSuccess { session ->
                _uiState.value = RecorderUiState(
                    phase = RecordingPhase.COMPLETE,
                    originalTranscript = session.originalTranscript,
                    cleanedTranscript = session.transcript,
                    selectedModel = _uiState.value.selectedModel,
                    sessionId = session.id,
                )
            }.onFailure(::showError)
        }
    }

    fun clear() {
        if (_uiState.value.phase != RecordingPhase.PROCESSING) {
            _uiState.update { RecorderUiState(selectedModel = it.selectedModel) }
        }
    }

    fun cancelRecording() {
        if (_uiState.value.phase != RecordingPhase.RECORDING) return
        timerJob?.cancel()
        viewModelScope.launch {
            runCatching { recorder.stop() }
                .onSuccess {
                    _uiState.update { RecorderUiState(selectedModel = it.selectedModel) }
                }
                .onFailure(::showError)
        }
    }

    fun processTranscript() {
        if (_uiState.value.phase != RecordingPhase.COMPLETE) return
        val sessionId = _uiState.value.sessionId
        if (sessionId == null) {
            showError(IllegalStateException("Save this transcript before processing it."))
            return
        }
        processSession(
            sessionId = sessionId,
            transcript = _uiState.value.cleanedTranscript,
            updateRecorderState = true,
        )
    }

    fun processSavedSession(sessionId: String) {
        val session = sessions.value.firstOrNull { it.id == sessionId } ?: return
        if (session.transcript.isBlank()) return
        if (session.status in setOf(SessionStatus.QUEUED, SessionStatus.PROCESSING, SessionStatus.PROCESSED)) return
        processSession(
            sessionId = session.id,
            transcript = session.transcript,
            updateRecorderState = _uiState.value.sessionId == session.id,
        )
    }

    private fun processSession(
        sessionId: String,
        transcript: String,
        updateRecorderState: Boolean,
    ) {
        if (processingJob?.isActive == true) return
        processingJob = viewModelScope.launch {
            runCatching {
                sessionRepository.updateStatus(sessionId, SessionStatus.QUEUED)
                sessionRepository.updateStatus(sessionId, SessionStatus.PROCESSING)
                if (updateRecorderState) {
                    _uiState.update { it.copy(phase = RecordingPhase.INTERPRETING, errorMessage = null) }
                }
                interpreter.interpret(transcript).also { result ->
                    sessionRepository.saveProcessed(sessionId, result)
                }
            }
                .onSuccess { result ->
                    if (updateRecorderState) {
                        _uiState.update { it.copy(phase = RecordingPhase.PROCESSED, processedMessage = result) }
                    }
                }
                .onFailure { error ->
                    runCatching { sessionRepository.updateStatus(sessionId, SessionStatus.FAILED) }
                    if (updateRecorderState) {
                        _uiState.update {
                            it.copy(
                                phase = RecordingPhase.COMPLETE,
                                errorMessage = error.message ?: "Gemma could not process this transcript.",
                            )
                        }
                    }
                }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.delete(id) }
                .onSuccess {
                    if (_uiState.value.sessionId == id) clear()
                }
                .onFailure(::showError)
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

    fun activeListeningBlocksManualRecording() {
        showError(IllegalStateException("Pause or turn off Keep an Ear Out before using Listen Now."))
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
        processingJob?.cancel()
        recorder.release()
        interpreter.release()
        super.onCleared()
    }
}
