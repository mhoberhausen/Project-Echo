package com.mobileobie.echo.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileobie.echo.audio.AudioRecorder
import com.mobileobie.echo.audio.IncrementalAudioRecorder
import com.mobileobie.echo.data.SessionRepository
import com.mobileobie.echo.interpretation.TranscriptInterpreter
import com.mobileobie.echo.model.RecorderUiState
import com.mobileobie.echo.model.RecordingPhase
import com.mobileobie.echo.model.SessionMetadata
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.transcription.Transcriber
import com.mobileobie.echo.transcription.IncrementalTranscriptionWorker
import com.mobileobie.echo.transcription.TimestampedTranscript
import com.mobileobie.echo.transcription.TranscriptCleaner
import com.mobileobie.echo.transcription.SpeakerDiarizer
import com.mobileobie.echo.transcription.SpeakerLabels
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
    private val diarizer: SpeakerDiarizer,
    private val cleaner: TranscriptCleaner,
    private val interpreter: TranscriptInterpreter,
    private val sessionRepository: SessionRepository,
    private val quietBoundaryMs: () -> Long = { 1_000L },
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()
    val sessions: StateFlow<List<SessionRecord>> = sessionRepository.sessions
    private var timerJob: Job? = null
    private var processingJob: Job? = null
    private var chunkWorker: IncrementalTranscriptionWorker? = null

    init {
        viewModelScope.launch {
            runCatching { sessionRepository.refresh() }.onFailure(::showError)
        }
    }

    fun startRecording() {
        if (_uiState.value.phase == RecordingPhase.RECORDING) return
        viewModelScope.launch {
            runCatching {
                if (recorder is IncrementalAudioRecorder) {
                    startChunkTranscription(_uiState.value.selectedModel)
                    recorder.startIncremental(quietBoundaryMs()) { chunk ->
                        chunkWorker?.offer(chunk)
                    }
                } else recorder.start()
            }
                .onSuccess {
                    _uiState.update {
                        RecorderUiState(
                            phase = RecordingPhase.RECORDING,
                            selectedModel = it.selectedModel,
                        )
                    }
                    startTimer()
                }
                .onFailure {
                    cancelChunkTranscription()
                    showError(it)
                }
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
                val timestamped = finishChunkTranscription()
                    ?: transcriber.transcribe(audio, model)
                val diarized = diarizer.diarize(audio, timestamped)
                val original = diarized.text
                val cleaned = cleaner.clean(original)
                val durationMillis = audio.samples.size * 1_000L / audio.sampleRateHz
                val session = SessionMetadata.create(
                    durationMillis = durationMillis,
                    transcript = cleaned,
                    originalTranscript = original,
                    transcriptSegments = diarized.segments,
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
                    speakerIds = session.transcriptSegments.mapNotNull { it.speakerId }.distinct(),
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
        cancelChunkTranscription()
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

    fun updateTranscript(sessionId: String, transcript: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.updateTranscript(sessionId, transcript) }
                .onSuccess {
                    if (_uiState.value.sessionId == sessionId) {
                        _uiState.update { state ->
                            state.copy(
                                phase = RecordingPhase.COMPLETE,
                                cleanedTranscript = transcript.trim(),
                                processedMessage = null,
                                errorMessage = null,
                                speakerIds = emptyList(),
                            )
                        }
                    }
                }
                .onFailure(::showError)
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.updateTitle(sessionId, title) }
                .onFailure(::showError)
        }
    }

    fun renameSpeakers(sessionId: String, names: Map<String, String>) {
        viewModelScope.launch {
            runCatching { sessionRepository.renameSpeakers(sessionId, names) }
                .onSuccess {
                    if (_uiState.value.sessionId == sessionId) {
                        _uiState.update { state ->
                            state.copy(
                                phase = RecordingPhase.COMPLETE,
                                cleanedTranscript = SpeakerLabels.renameInText(state.cleanedTranscript, names),
                                originalTranscript = SpeakerLabels.renameInText(state.originalTranscript, names),
                                processedMessage = null,
                                errorMessage = null,
                                speakerIds = state.speakerIds.map { names.getValue(it) },
                            )
                        }
                    }
                }
                .onFailure(::showError)
        }
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
                    Log.e(LOG_TAG, "Saved transcript interpretation failed for session $sessionId", error)
                    runCatching { sessionRepository.updateStatus(sessionId, SessionStatus.FAILED) }
                    if (updateRecorderState) {
                        _uiState.update {
                            it.copy(
                                phase = RecordingPhase.COMPLETE,
                                errorMessage = error.message ?: "The selected AI method could not process this transcript.",
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

    fun aiNetworkPermissionDenied() {
        showError(IllegalStateException("Local network permission is required to use the selected LAN AI method."))
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

    private fun startChunkTranscription(model: TranscriptionModel) {
        cancelChunkTranscription()
        chunkWorker = IncrementalTranscriptionWorker(
            viewModelScope,
            transcriber,
            model,
            onDiagnostic = { Log.i(LOG_TAG, it) },
        )
    }

    private suspend fun finishChunkTranscription(): TimestampedTranscript? {
        val worker = chunkWorker ?: return null
        val transcript = worker.finish()
        chunkWorker = null
        return transcript
    }

    private fun cancelChunkTranscription() {
        chunkWorker?.cancel()
        chunkWorker = null
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
        cancelChunkTranscription()
        recorder.release()
        interpreter.release()
    }

    private companion object {
        const val LOG_TAG = "RecorderViewModel"
    }
}
