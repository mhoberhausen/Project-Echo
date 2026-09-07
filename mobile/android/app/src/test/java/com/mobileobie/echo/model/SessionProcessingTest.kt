package com.mobileobie.echo.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionProcessingTest {
    @Test fun diarizationFailureKeepsUsableTranscriptReadyWithIssues() {
        val processing = SessionProcessing().withStage(ProcessingStage.ASSEMBLY, StageProgress(StageState.COMPLETE))
            .withStage(ProcessingStage.DIARIZATION, StageProgress(StageState.FAILED, failure = ProcessingFailure(ProcessingStage.DIARIZATION, true, "Speaker identification could not be completed.")))
        assertEquals(SessionReadiness.READY_WITH_ISSUES, processing.readiness(hasTranscript = true))
    }

    @Test fun interpretationDoesNotChangeTranscriptReadiness() {
        val processing = SessionProcessing().withStage(ProcessingStage.ASSEMBLY, StageProgress(StageState.COMPLETE))
            .withStage(ProcessingStage.INTERPRETATION, StageProgress(StageState.FAILED))
        assertEquals(SessionReadiness.READY, processing.readiness(hasTranscript = true))
    }
}
