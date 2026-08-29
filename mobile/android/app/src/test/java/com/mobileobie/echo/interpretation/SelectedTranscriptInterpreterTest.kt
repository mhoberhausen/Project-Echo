package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.MessageIntent
import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.settings.AiProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SelectedTranscriptInterpreterTest {
    private val result = ProcessedMessage("Summary", MessageIntent.NOTE, emptyList(), emptyList(), emptyList())

    @Test
    fun delegatesToEnabledAvailableOnDeviceProvider() = runBlocking {
        val interpreter = SelectedTranscriptInterpreter(
            providers = { listOf(AiProviderConfig.ON_DEVICE_GEMMA) },
            onDeviceInterpreter = stubInterpreter(),
        )

        assertEquals(result, interpreter.interpret("Transcript"))
    }

    @Test
    fun rejectsInferenceWhenAllAvailableProvidersAreDisabled() {
        val interpreter = SelectedTranscriptInterpreter(
            providers = { listOf(AiProviderConfig.ON_DEVICE_GEMMA.copy(enabled = false)) },
            onDeviceInterpreter = stubInterpreter(),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { interpreter.interpret("Transcript") }
        }
        assertEquals("Enable an available AI method in Settings > AI Selection.", error.message)
    }

    private fun stubInterpreter() = object : TranscriptInterpreter {
        override suspend fun interpret(transcript: String) = result
    }
}
