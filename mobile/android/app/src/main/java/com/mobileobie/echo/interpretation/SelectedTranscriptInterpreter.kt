package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.settings.AiProviderConfig

/** Applies the persisted AI selection without coupling the UI to an inference runtime. */
class SelectedTranscriptInterpreter(
    private val providers: () -> List<AiProviderConfig>,
    private val onDeviceInterpreter: TranscriptInterpreter,
) : TranscriptInterpreter {
    override suspend fun interpret(transcript: String): ProcessedMessage {
        val selected = providers().firstOrNull { it.enabled && it.available }
            ?: error("Enable an available AI method in Settings > AI Selection.")
        check(selected.id == AiProviderConfig.ON_DEVICE_GEMMA.id) {
            "${selected.name} is configured but its connector is not installed."
        }
        return onDeviceInterpreter.interpret(transcript)
    }

    override fun release() = onDeviceInterpreter.release()
}
