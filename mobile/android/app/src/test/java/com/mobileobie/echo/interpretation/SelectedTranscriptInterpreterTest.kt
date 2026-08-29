package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.MessageIntent
import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.settings.AiProviderConfig
import com.mobileobie.echo.settings.AiProviderKind
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

    @Test
    fun executesAvailableExternalProviderWhenItHasPriority() = runBlocking {
        val external = externalProvider(available = true)
        val externalResult = result.copy(summary = "External")
        val interpreter = SelectedTranscriptInterpreter(
            providers = { listOf(external, AiProviderConfig.ON_DEVICE_GEMMA) },
            interpreterFor = mapOf(
                external.id to stubInterpreter(externalResult),
                AiProviderConfig.ON_DEVICE_GEMMA.id to stubInterpreter(),
            ).let { runtimes -> { provider -> runtimes[provider.id] } },
        )

        assertEquals(externalResult, interpreter.interpret("Transcript"))
    }

    @Test
    fun fallsBackToOnDeviceWhenExternalProviderIsUnavailable() = runBlocking {
        val external = externalProvider(available = false)
        val interpreter = SelectedTranscriptInterpreter(
            providers = { listOf(external, AiProviderConfig.ON_DEVICE_GEMMA) },
            interpreterFor = mapOf(
                external.id to stubInterpreter(result.copy(summary = "External")),
                AiProviderConfig.ON_DEVICE_GEMMA.id to stubInterpreter(),
            ).let { runtimes -> { provider -> runtimes[provider.id] } },
        )

        assertEquals(result, interpreter.interpret("Transcript"))
    }

    @Test
    fun fallsBackToOnDeviceWhenExternalExecutionFails() = runBlocking {
        val external = externalProvider(available = true)
        val failingExternal = object : TranscriptInterpreter {
            override suspend fun interpret(transcript: String): ProcessedMessage = error("Offline")
        }
        val interpreter = SelectedTranscriptInterpreter(
            providers = { listOf(external, AiProviderConfig.ON_DEVICE_GEMMA) },
            interpreterFor = mapOf(
                external.id to failingExternal,
                AiProviderConfig.ON_DEVICE_GEMMA.id to stubInterpreter(),
            ).let { runtimes -> { provider -> runtimes[provider.id] } },
        )

        assertEquals(result, interpreter.interpret("Transcript"))
    }

    private fun externalProvider(available: Boolean) = AiProviderConfig(
        id = "external-test",
        name = "External test provider",
        kind = AiProviderKind.LAN,
        endpoint = "http://127.0.0.1:4242/v1",
        available = available,
    )

    private fun stubInterpreter(value: ProcessedMessage = result) = object : TranscriptInterpreter {
        override suspend fun interpret(transcript: String) = value
    }
}
