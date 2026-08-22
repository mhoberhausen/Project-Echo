package com.echokeep.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.echokeep.app.model.RecorderUiState
import com.echokeep.app.model.SessionRecord
import com.echokeep.app.model.SessionStatus
import com.echokeep.app.model.TranscriptionModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EchoKeepAppSessionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pendingSavedSessionCanBeProcessedFromDetail() {
        val session = SessionRecord(
            id = "pending-session",
            createdAtUtcMillis = 1_704_067_200_000,
            updatedAtUtcMillis = 1_704_067_200_000,
            durationMillis = 12_000,
            title = "Process me",
            status = SessionStatus.TRANSCRIBED,
            transcript = "A saved transcript.",
            originalTranscript = "A saved transcript.",
            processText = null,
            tags = emptyList(),
            transcriptionModel = TranscriptionModel.FAST,
        )
        var processedSessionId: String? = null

        composeRule.setContent {
            EchoKeepTheme {
                EchoKeepApp(
                    recorderState = RecorderUiState(),
                    sessions = listOf(session),
                    darkTheme = false,
                    silenceSeconds = 1.5f,
                    onDarkThemeChanged = {},
                    onSilenceSecondsChanged = {},
                    onRecord = {},
                    onStop = {},
                    onClear = {},
                    onModelSelected = {},
                    onProcess = {},
                    onCancelRecording = {},
                    onDeleteSession = {},
                    onProcessSession = { processedSessionId = it },
                    onShareSession = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Process me").performClick()
        composeRule.onNodeWithText("Process").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals("pending-session", processedSessionId) }
    }
}
