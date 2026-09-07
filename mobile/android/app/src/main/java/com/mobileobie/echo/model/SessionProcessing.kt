package com.mobileobie.echo.model

/** Durable, user-safe provenance for automatic processing and optional interpretation. */
enum class ProcessingStage { CAPTURE, CHUNKING, TRANSCRIPTION, ASSEMBLY, DIARIZATION, INTERPRETATION }
enum class StageState { NOT_STARTED, QUEUED, RUNNING, COMPLETE, FAILED, CANCELED }
enum class SessionReadiness { CAPTURING, PROCESSING, READY, READY_WITH_ISSUES, FAILED }

data class ProcessingFailure(
    val stage: ProcessingStage,
    val retryable: Boolean,
    val userMessage: String,
    val chunkIndex: Int? = null,
    val debugCode: String? = null,
)

data class StageProgress(
    val state: StageState = StageState.NOT_STARTED,
    val attempts: Int = 0,
    val startedAtUtcMillis: Long? = null,
    val completedAtUtcMillis: Long? = null,
    val failure: ProcessingFailure? = null,
)

data class SessionProcessing(
    val stages: Map<ProcessingStage, StageProgress> = emptyMap(),
    val chunkCount: Int = 0,
    val completedChunkCount: Int = 0,
) {
    fun stage(stage: ProcessingStage): StageProgress = stages[stage] ?: StageProgress()
    fun withStage(stage: ProcessingStage, progress: StageProgress) = copy(stages = stages + (stage to progress))

    fun readiness(hasTranscript: Boolean): SessionReadiness {
        val automatic = listOf(ProcessingStage.CAPTURE, ProcessingStage.CHUNKING, ProcessingStage.TRANSCRIPTION, ProcessingStage.ASSEMBLY, ProcessingStage.DIARIZATION)
        if (stage(ProcessingStage.CAPTURE).state == StageState.RUNNING) return SessionReadiness.CAPTURING
        if (hasTranscript && stage(ProcessingStage.DIARIZATION).state == StageState.FAILED) return SessionReadiness.READY_WITH_ISSUES
        if (hasTranscript && stage(ProcessingStage.ASSEMBLY).state == StageState.COMPLETE) return SessionReadiness.READY
        if (automatic.any { stage(it).state in setOf(StageState.QUEUED, StageState.RUNNING) }) return SessionReadiness.PROCESSING
        return if (hasTranscript) SessionReadiness.READY else SessionReadiness.FAILED
    }

    companion object {
        fun legacy(status: SessionStatus, hasTranscript: Boolean): SessionProcessing {
            val complete = StageProgress(StageState.COMPLETE)
            val failed = StageProgress(StageState.FAILED, failure = ProcessingFailure(ProcessingStage.TRANSCRIPTION, true, "Speech-to-text could not be completed."))
            return when (status) {
                SessionStatus.CAPTURING -> SessionProcessing(mapOf(ProcessingStage.CAPTURE to StageProgress(StageState.RUNNING)))
                SessionStatus.TRANSCRIBING -> SessionProcessing(mapOf(ProcessingStage.CAPTURE to complete, ProcessingStage.TRANSCRIPTION to StageProgress(StageState.QUEUED)))
                SessionStatus.TRANSCRIPTION_FAILED -> SessionProcessing(mapOf(ProcessingStage.CAPTURE to complete, ProcessingStage.TRANSCRIPTION to failed))
                else -> SessionProcessing(
                    stages = mapOf(
                        ProcessingStage.CAPTURE to complete, ProcessingStage.CHUNKING to complete,
                        ProcessingStage.TRANSCRIPTION to complete, ProcessingStage.ASSEMBLY to complete,
                        ProcessingStage.DIARIZATION to if (hasTranscript) complete else StageProgress(),
                        ProcessingStage.INTERPRETATION to if (status == SessionStatus.PROCESSED) complete else StageProgress(),
                    ),
                )
            }
        }
    }
}
