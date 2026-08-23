package com.huh.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huh.app.active.ActiveListeningSnapshot
import com.huh.app.active.ActiveListeningState
import com.huh.app.model.RecorderUiState
import com.huh.app.model.SessionRecord
import com.huh.app.model.SessionStatus
import com.huh.app.model.TranscriptionModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HuhAppSessionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeUsesHuhBrandAndHonestModeLabels() {
        composeRule.setContent {
            HuhTheme {
                HuhApp(
                    recorderState = RecorderUiState(),
                    sessions = emptyList(),
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
                    onProcessSession = {},
                    onShareSession = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Huh?").assertCountEquals(2)
        composeRule.onNodeWithText("Never miss what was said.").assertIsDisplayed()
        composeRule.onNodeWithText("LISTEN NOW").assertIsDisplayed()
        composeRule.onNodeWithText("KEEP AN EAR OUT").assertIsDisplayed()
        composeRule.onNodeWithText("Continuous local listening").assertIsDisplayed()

        composeRule.onNodeWithText("LISTEN NOW").performClick()
        composeRule.onAllNodesWithContentDescription("Open navigation").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("LISTEN NOW").assertIsDisplayed()
    }

    @Test
    fun advancedSettingsShowsTimingDefaultsAndBackReturnsToSettings() {
        var resetRequested = false
        composeRule.setContent {
            HuhTheme {
                HuhApp(
                    recorderState = RecorderUiState(),
                    sessions = emptyList(),
                    darkTheme = false,
                    silenceSeconds = 15f,
                    onDarkThemeChanged = {},
                    onSilenceSecondsChanged = {},
                    onRecord = {},
                    onStop = {},
                    onClear = {},
                    onModelSelected = {},
                    onProcess = {},
                    onCancelRecording = {},
                    onDeleteSession = {},
                    onProcessSession = {},
                    onShareSession = {},
                    speechStartThresholdMs = 400f,
                    minimumTranscriptSpeechSeconds = 3f,
                    preRollSeconds = 2f,
                    minimumTranscriptWords = 3f,
                    onResetAdvancedDefaults = { resetRequested = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Advanced conversation settings").performClick()
        composeRule.onNodeWithText("Terms used here").assertIsDisplayed()
        composeRule.onNodeWithText("VAD — Voice Activity Detection").assertIsDisplayed()
        composeRule.onNodeWithText("400 ms of sustained speech").assertIsDisplayed()
        composeRule.onNodeWithText("At least 3 seconds of detected speech").assertIsDisplayed()
        composeRule.onNodeWithText("End after 15 seconds without speech").assertIsDisplayed()
        composeRule.onNodeWithText("Reset to defaults").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, resetRequested) }
        composeRule.onNodeWithContentDescription("Navigate back").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun drawerShowsTemporarySessionWhileActiveCaptureIsRecording() {
        composeRule.setContent {
            HuhTheme {
                HuhApp(
                    recorderState = RecorderUiState(),
                    sessions = emptyList(),
                    darkTheme = false,
                    silenceSeconds = 15f,
                    onDarkThemeChanged = {},
                    onSilenceSecondsChanged = {},
                    onRecord = {},
                    onStop = {},
                    onClear = {},
                    onModelSelected = {},
                    onProcess = {},
                    onCancelRecording = {},
                    onDeleteSession = {},
                    onProcessSession = {},
                    onShareSession = {},
                    activeListening = ActiveListeningSnapshot(
                        state = ActiveListeningState.LISTENING,
                        captureStartedAtUtcMillis = 1_704_067_200_000,
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Conversation in progress").assertIsDisplayed()
        composeRule.onNodeWithText("Recording locally · Not yet transcribed").assertIsDisplayed()
        composeRule.onNodeWithText("Recording…").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("I’m listening…").assertIsDisplayed()
    }

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
            HuhTheme {
                HuhApp(
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
        composeRule.onAllNodesWithText("Listen").assertCountEquals(0)
        composeRule.onNodeWithText("Process me").performClick()
        composeRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Open navigation").assertCountEquals(0)
        composeRule.onNodeWithText("Make sense of this").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals("pending-session", processedSessionId) }
    }
}
