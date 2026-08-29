package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.settings.AiProviderConfig

/** Applies the persisted AI selection without coupling the UI to an inference runtime. */
class SelectedTranscriptInterpreter(
    private val providers: () -> List<AiProviderConfig>,
    private val interpreterFor: (AiProviderConfig) -> TranscriptInterpreter?,
) : TranscriptInterpreter {
    private val resolvedInterpreters = mutableSetOf<TranscriptInterpreter>()

    constructor(
        providers: () -> List<AiProviderConfig>,
        onDeviceInterpreter: TranscriptInterpreter,
    ) : this(
        providers = providers,
        interpreterFor = { provider ->
            onDeviceInterpreter.takeIf { provider.id == AiProviderConfig.ON_DEVICE_GEMMA.id }
        },
    )

    override suspend fun interpret(transcript: String): ProcessedMessage {
        var lastFailure: Throwable? = null
        providers().asSequence()
            .filter { it.enabled && it.available }
            .forEach { provider ->
                val interpreter = interpreterFor(provider)?.also(resolvedInterpreters::add)
                    ?: return@forEach
                runCatching { interpreter.interpret(transcript) }
                    .onSuccess { return it }
                    .onFailure { lastFailure = it }
            }
        lastFailure?.let { failure ->
            throw IllegalStateException(
                "The enabled AI methods could not process this transcript.",
                failure,
            )
        }
        error("Enable an available AI method in Settings > AI Selection.")
    }

    override fun release() = resolvedInterpreters.forEach(TranscriptInterpreter::release)
}
