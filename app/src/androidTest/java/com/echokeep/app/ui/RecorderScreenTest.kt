package com.echokeep.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echokeep.app.model.MessageIntent
import com.echokeep.app.model.ProcessedMessage
import com.echokeep.app.model.RecorderUiState
import com.echokeep.app.model.RecordingPhase
import org.junit.Rule
import org.junit.Test

class RecorderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun processedResultWithoutActionItemsRenders() {
        composeRule.setContent {
            EchoKeepTheme {
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

        composeRule.onNodeWithText("Processed message").assertIsDisplayed()
        composeRule.onNodeWithText("A useful summary.").assertIsDisplayed()
        composeRule.onNodeWithText("Intent · Note").assertIsDisplayed()
        composeRule.onNodeWithText("A key point", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Show transcript").assertIsDisplayed()
    }
}
