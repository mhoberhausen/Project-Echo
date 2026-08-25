package com.mobileobie.echo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.mobileobie.echo.model.MessageIntent
import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.model.RecorderUiState
import com.mobileobie.echo.model.RecordingPhase
import org.junit.Rule
import org.junit.Test

class RecorderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleBrandControlHasListeningSemantics() {
        composeRule.setContent {
            HuhTheme {
                RecorderScreen(
                    state = RecorderUiState(),
                    onRecord = {},
                    onStop = {},
                    onClear = {},
                    onModelSelected = {},
                    onProcess = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Start listening").assertIsDisplayed()
        composeRule.onNodeWithText("Ready when you are.").assertIsDisplayed()
        composeRule.onNodeWithText("Processed on this device").assertIsDisplayed()
    }

    @Test
    fun processedResultWithoutActionItemsRenders() {
        composeRule.setContent {
            HuhTheme {
                RecorderScreen(
                    state = RecorderUiState(
                        phase = RecordingPhase.PROCESSED,
                        cleanedTranscript = "A concise transcript.",
                        processedMessage = ProcessedMessage(
                            summary = "A useful summary.",
                            intent = MessageIntent.NOTE,
                            keyPoints = listOf("A key point"),
                            actionItems = emptyList(),
                        ),
                    ),
                    onRecord = {},
                    onStop = {},
                    onClear = {},
                    onModelSelected = {},
                    onProcess = {},
                )
            }
        }

        composeRule.onNodeWithText("What I Got From It").assertIsDisplayed()
        composeRule.onNodeWithText("A useful summary.").assertIsDisplayed()
        composeRule.onNodeWithText("Intent · Note").assertIsDisplayed()
        composeRule.onNodeWithText("A key point", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Show what was said").assertIsDisplayed()
    }
}
