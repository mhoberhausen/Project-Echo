package com.mobileobie.echo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.mobileobie.echo.active.ActiveListeningSnapshot
import com.mobileobie.echo.active.ActiveListeningState
import com.mobileobie.echo.external.ExternalDeviceEndpoint
import com.mobileobie.echo.model.RecorderUiState
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.model.TranscriptSegment
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
        composeRule.onNodeWithContentDescription("Choose audio device").performClick()
        composeRule.onNodeWithText("Audio device").assertIsDisplayed()
        composeRule.onNodeWithText("This phone").assertIsDisplayed()
        composeRule.onNodeWithText("Bluetooth device").assertIsDisplayed()
        composeRule.onNodeWithText("Huh? Puck").assertIsDisplayed()
        composeRule.onNodeWithText("Add device").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

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
        composeRule.onNodeWithText("At least 3 seconds of detected speech").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("End after 15 seconds without speech").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reset to defaults").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, resetRequested) }
        composeRule.onNodeWithContentDescription("Navigate back").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("AI Selection").performScrollTo().performClick()
        composeRule.onNodeWithText("Gemma 3 1B").assertIsDisplayed()
        composeRule.onNodeWithText("Add AI method").performClick()
        composeRule.onNodeWithText("Endpoint URL").assertIsDisplayed()
    }

    @Test
    fun supportIsOptionalAndConfiguredActionStaysWithinSettingsNavigation() {
        var supportOpened = false
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
                    supportAvailable = true,
                    onSupportDeveloper = { supportOpened = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Get help or support Huh?").performScrollTo().performClick()
        composeRule.onNodeWithText("Help & support").assertIsDisplayed()
        composeRule.onNodeWithText("Find help using Huh?, or optionally support the developer. Huh? is free to use.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Open Huh? support").performClick()
        composeRule.runOnIdle { assertEquals(true, supportOpened) }
        composeRule.onNodeWithContentDescription("Navigate back").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun unconfiguredSupportIsUnavailableWithoutChangingSettings() {
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
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Support the developer").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Developer support is not configured in this build.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun externalDeviceSettingsExplainPocAndSubmitValidatedEndpoint() {
        var connectedEndpoint: ExternalDeviceEndpoint? = null
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
                    externalDeviceEndpoint = ExternalDeviceEndpoint(host = "192.168.1.40"),
                    onConnectExternalDevice = { connectedEndpoint = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Configure Huh? Puck").performClick()
        composeRule.onNodeWithText("Trusted-LAN POC").assertIsDisplayed()
        composeRule.onNodeWithText("Save and connect").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("192.168.1.40", connectedEndpoint?.host)
            assertEquals(8_765, connectedEndpoint?.port)
        }
        composeRule.onNodeWithContentDescription("Navigate back").performClick()
        composeRule.onNodeWithText("External Device").assertIsDisplayed()
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
            transcriptionModel = TranscriptionModel.ACCURATE,
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

    @Test
    fun processedSessionTranscriptCanBeEditedWithInferenceWarning() {
        val session = SessionRecord(
            id = "processed-session",
            createdAtUtcMillis = 1_704_067_200_000,
            updatedAtUtcMillis = 1_704_067_200_000,
            durationMillis = 12_000,
            title = "Processed session",
            status = SessionStatus.PROCESSED,
            transcript = "Original transcript.",
            originalTranscript = "Original transcript.",
            processText = "Existing inference.",
            tags = listOf("Example"),
            transcriptionModel = TranscriptionModel.ACCURATE,
        )
        var edited: Pair<String, String>? = null

        composeRule.setContent {
            HuhTheme {
                HuhApp(
                    recorderState = RecorderUiState(),
                    sessions = listOf(session),
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
                    onEditTranscript = { id, transcript -> edited = id to transcript },
                    onShareSession = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Processed session").performClick()
        composeRule.onNodeWithText("Transcript").performClick()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText(
            "Saving changes removes the current inference. You can run Make sense of this again."
        ).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Corrected transcript.")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            assertEquals("processed-session" to "Corrected transcript.", edited)
        }
    }

    @Test
    fun diarizedSessionSpeakersCanBeNamed() {
        val session = SessionRecord(
            id = "diarized-session",
            createdAtUtcMillis = 1_704_067_200_000,
            updatedAtUtcMillis = 1_704_067_200_000,
            durationMillis = 12_000,
            title = "Two speakers",
            status = SessionStatus.TRANSCRIBED,
            transcript = "Speaker 1: Hello.\nSpeaker 2: Hi.",
            originalTranscript = "Speaker 1: Hello.\nSpeaker 2: Hi.",
            processText = null,
            tags = emptyList(),
            transcriptionModel = TranscriptionModel.ACCURATE,
            transcriptSegments = listOf(
                TranscriptSegment(0, 900, "Hello.", "speaker-1"),
                TranscriptSegment(1_000, 1_900, "Hi.", "speaker-2"),
            ),
        )
        var renamed: Pair<String, Map<String, String>>? = null

        composeRule.setContent {
            HuhTheme {
                HuhApp(
                    recorderState = RecorderUiState(),
                    sessions = listOf(session),
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
                    onRenameSpeakers = { id, names -> renamed = id to names },
                    onShareSession = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Two speakers").performClick()
        composeRule.onNodeWithText("Transcript").performClick()
        composeRule.onNodeWithText("Name speakers").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Alice")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("Bob")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            assertEquals(
                "diarized-session" to mapOf("speaker-1" to "Alice", "speaker-2" to "Bob"),
                renamed,
            )
        }
    }

    @Test
    fun sessionContentExpandsRenamesAndSharesOnlySelectedContent() {
        val session = SessionRecord(
            id = "share-session",
            createdAtUtcMillis = 1_704_067_200_000,
            updatedAtUtcMillis = 1_704_067_200_000,
            durationMillis = 12_000,
            title = "Old name",
            status = SessionStatus.PROCESSED,
            transcript = "Transcript content only.",
            originalTranscript = "Transcript content only.",
            processText = "Inferred content only.",
            tags = listOf("Example"),
            transcriptionModel = TranscriptionModel.ACCURATE,
        )
        var renamed: Pair<String, String>? = null
        var shared: String? = null

        composeRule.setContent {
            HuhTheme {
                HuhApp(
                    recorderState = RecorderUiState(),
                    sessions = listOf(session),
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
                    onRenameSession = { id, title -> renamed = id to title },
                    onShareSession = { shared = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Old name").performClick()
        composeRule.onAllNodesWithText("Transcript content only.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Inferred content only.").assertCountEquals(0)
        composeRule.onNodeWithText("Transcript").performClick()
        composeRule.onNodeWithText("Transcript content only.").assertIsDisplayed()
        composeRule.onNodeWithText("Inferred summary").performClick()
        composeRule.onNodeWithText("Inferred content only.", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Share").performClick()
        composeRule.onNodeWithText("Share inferred summary").performClick()
        composeRule.runOnIdle { assertEquals("Inferred content only.", shared) }

        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("New name")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.runOnIdle { assertEquals("share-session" to "New name", renamed) }
    }

    @Test
    fun transcribedSessionUsesReadyLanguage() {
        val session = SessionRecord(
            id = "ready-session",
            createdAtUtcMillis = 1_704_067_200_000,
            updatedAtUtcMillis = 1_704_067_200_000,
            durationMillis = 12_000,
            title = "A deliberately long session title that keeps its full-width hierarchy",
            status = SessionStatus.TRANSCRIBED,
            transcript = "Transcript content.",
            originalTranscript = "Transcript content.",
            processText = null,
            tags = emptyList(),
            transcriptionModel = TranscriptionModel.ACCURATE,
        )

        composeRule.setContent {
            HuhTheme {
                HuhApp(
                    recorderState = RecorderUiState(),
                    sessions = listOf(session),
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
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Transcript ready").assertIsDisplayed()
        composeRule.onNodeWithText(session.title).performClick()
        composeRule.onAllNodesWithText("Transcript ready", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
    }
}
