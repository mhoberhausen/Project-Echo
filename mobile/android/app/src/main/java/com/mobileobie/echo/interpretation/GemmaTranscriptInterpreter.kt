package com.mobileobie.echo.interpretation

import android.content.Context
import android.util.Log
import com.mobileobie.echo.model.ActionItem
import com.mobileobie.echo.model.MessageIntent
import com.mobileobie.echo.model.ProcessedMessage
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GemmaTranscriptInterpreter(private val context: Context) : TranscriptInterpreter {
    private var engine: Engine? = null

    override suspend fun interpret(transcript: String): ProcessedMessage = withContext(Dispatchers.IO) {
        require(transcript.isNotBlank()) { "There is no transcript to process." }
        val activeEngine = engine ?: createEngine().also { engine = it }
        val config = ConversationConfig(
            systemInstruction = Contents.of(TranscriptInterpretationPrompt.SYSTEM_INSTRUCTION),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
            maxOutputToken = 512,
        )
        activeEngine.createConversation(config).use { conversation ->
            val raw = conversation.sendMessage(TranscriptInterpretationPrompt.forTranscript(transcript)).toString()
            runCatching { GemmaResponseParser.parse(raw) }
                .onFailure { Log.w(LOG_TAG, "Gemma returned malformed structured output", it) }
                .getOrElse { conservativeResult(transcript) }
                .validatedAgainstTranscript(transcript)
        }
    }

    private fun createEngine(): Engine {
        val modelFile = ensureModelFile()
        return Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
            )
        ).also { it.initialize() }
    }

    private fun ensureModelFile(): File {
        val destination = File(context.filesDir, MODEL_FILENAME)
        if (destination.length() == MODEL_SIZE_BYTES) return destination
        val temporary = File(context.filesDir, "$MODEL_FILENAME.partial")
        temporary.delete()
        context.assets.open("models/$MODEL_FILENAME").use { input ->
            temporary.outputStream().buffered().use(input::copyTo)
        }
        check(temporary.length() == MODEL_SIZE_BYTES) { "The bundled Gemma model is incomplete." }
        check(temporary.renameTo(destination)) { "Could not prepare the bundled Gemma model." }
        return destination
    }

    override fun release() {
        engine?.close()
        engine = null
    }

    private fun conservativeResult(transcript: String) = ProcessedMessage(
        summary = transcript.lineSequence().joinToString(" ") { it.trim() }.trim().take(240),
        intent = MessageIntent.UNKNOWN,
        keyPoints = emptyList(),
        actionItems = emptyList(),
        tags = emptyList(),
    )

    private companion object {
        const val MODEL_FILENAME = "gemma3-1b-it-int4.litertlm"
        const val MODEL_SIZE_BYTES = 584_417_280L
        const val LOG_TAG = "GemmaInterpreter"
    }
}

internal fun ProcessedMessage.validatedAgainstTranscript(transcript: String): ProcessedMessage {
    val canContainActions = intent == MessageIntent.TASK || intent == MessageIntent.REMINDER
    return copy(
        actionItems = if (canContainActions) actionItems.map { action ->
            action.copy(dueDate = action.dueDate?.takeIf { transcript.contains(it, ignoreCase = true) })
        } else emptyList(),
    )
}

internal object TranscriptInterpretationPrompt {
    const val SYSTEM_INSTRUCTION =
        "You organize voice transcripts into concise, faithful structured notes. Output valid JSON only."

    fun forTranscript(transcript: String) = """
        Process the transcript below. Do not invent facts, dates, or tasks. Return only one JSON object with exactly this schema:
        {"summary":"string","intent":"note|idea|reminder|task|conversation|question|unknown","key_points":["string"],"action_items":[{"text":"string","due_date":null}],"tags":["short topic"]}

        key_points, action_items, and tags MUST always be JSON arrays, even when they contain one item. Never return null or a string for an array field. Use an empty array when there are no key points, action items, or useful topic tags. Use at most five concise tags. Preserve an explicit due date as spoken; otherwise use null.

        TRANSCRIPT:
        $transcript
    """.trimIndent()
}

internal object GemmaResponseParser {
    fun parse(raw: String): ProcessedMessage {
        val jsonText = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .closeUnterminatedContainers()
        val json = JSONObject(jsonText)
        val required = setOf("summary", "intent", "key_points")
        val missing = required - json.keys().asSequence().toSet()
        require(missing.isEmpty()) {
            "Gemma omitted required fields: ${missing.sorted().joinToString()}."
        }
        val summary = json.getString("summary").trim()
        require(summary.isNotEmpty()) { "Gemma returned an empty summary." }
        val points = json.stringList("key_points")
        val actions = json.optionalArray("action_items")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    if (array.isNull(index)) continue
                val item = array.getJSONObject(index)
                require(item.keys().asSequence().toSet().containsAll(setOf("text", "due_date"))) {
                    "Gemma returned an incomplete action item."
                }
                    add(ActionItem(
                    text = item.getString("text").trim().also { require(it.isNotEmpty()) },
                    dueDate = if (item.isNull("due_date")) null else item.getString("due_date").trim().ifEmpty { null },
                    ))
                }
            }
        } ?: emptyList()
        return ProcessedMessage(
            summary = summary,
            intent = MessageIntent.fromWireValue(json.getString("intent")),
            keyPoints = points,
            actionItems = actions,
            tags = json.stringList("tags", required = false).distinctBy(String::lowercase),
        )
    }

    private fun JSONObject.stringList(key: String, required: Boolean = true): List<String> {
        if (!has(key) || isNull(key)) {
            require(!required) { "Gemma omitted required fields: $key." }
            return emptyList()
        }
        return when (val value = get(key)) {
            is JSONArray -> value.strings()
            is String -> listOfNotNull(value.trim().takeIf(String::isNotEmpty))
            else -> throw IllegalArgumentException("Gemma returned $key in an unexpected format.")
        }
    }

    private fun JSONObject.optionalArray(key: String): JSONArray? {
        if (!has(key) || isNull(key)) return null
        return when (val value = get(key)) {
            is JSONArray -> value
            is JSONObject -> JSONArray().put(value)
            else -> throw IllegalArgumentException("Gemma returned $key in an unexpected format.")
        }
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) {
            if (isNull(index)) continue
            getString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }

    private fun String.closeUnterminatedContainers(): String {
        val stack = ArrayDeque<Char>()
        var quoted = false
        var escaped = false
        for (character in this) {
            if (escaped) {
                escaped = false
            } else if (character == '\\' && quoted) {
                escaped = true
            } else if (character == '"') {
                quoted = !quoted
            } else if (!quoted) {
                when (character) {
                    '{', '[' -> stack.addLast(character)
                    '}' -> if (stack.lastOrNull() == '{') stack.removeLast()
                    ']' -> if (stack.lastOrNull() == '[') stack.removeLast()
                }
            }
        }
        if (quoted) return this
        return buildString {
            append(this@closeUnterminatedContainers)
            while (stack.isNotEmpty()) append(if (stack.removeLast() == '{') '}' else ']')
        }
    }
}
