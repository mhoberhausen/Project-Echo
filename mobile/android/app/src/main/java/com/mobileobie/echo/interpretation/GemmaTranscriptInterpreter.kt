package com.mobileobie.echo.interpretation

import android.content.Context
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
            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
        )
        activeEngine.createConversation(config).use { conversation ->
            GemmaResponseParser.parse(conversation.sendMessage(prompt(transcript)).toString())
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

    private fun prompt(transcript: String) = """
        Process the transcript below. Do not invent facts, dates, or tasks. Return only one JSON object with exactly this schema:
        {"summary":"string","intent":"note|idea|reminder|task|conversation|question|unknown","key_points":["string"],"action_items":[{"text":"string","due_date":null}],"tags":["short topic"]}

        Use an empty array when there are no key points, action items, or useful topic tags. Use at most five concise tags. Preserve an explicit due date as spoken; otherwise use null.

        TRANSCRIPT:
        $transcript
    """.trimIndent()

    private companion object {
        const val MODEL_FILENAME = "gemma3-1b-it-int4.litertlm"
        const val MODEL_SIZE_BYTES = 584_417_280L
        const val SYSTEM_INSTRUCTION = "You organize voice transcripts into concise, faithful structured notes. Output valid JSON only."
    }
}

internal object GemmaResponseParser {
    fun parse(raw: String): ProcessedMessage {
        val jsonText = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(jsonText)
        val required = setOf("summary", "intent", "key_points")
        val missing = required - json.keys().asSequence().toSet()
        require(missing.isEmpty()) {
            "Gemma omitted required fields: ${missing.sorted().joinToString()}."
        }
        val summary = json.getString("summary").trim()
        require(summary.isNotEmpty()) { "Gemma returned an empty summary." }
        val points = json.getJSONArray("key_points").strings()
        val actions = json.optJSONArray("action_items")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                require(item.keys().asSequence().toSet().containsAll(setOf("text", "due_date"))) {
                    "Gemma returned an incomplete action item."
                }
                ActionItem(
                    text = item.getString("text").trim().also { require(it.isNotEmpty()) },
                    dueDate = if (item.isNull("due_date")) null else item.getString("due_date").trim().ifEmpty { null },
                )
            }
        } ?: emptyList()
        return ProcessedMessage(
            summary = summary,
            intent = MessageIntent.fromWireValue(json.getString("intent")),
            keyPoints = points,
            actionItems = actions,
            tags = json.optJSONArray("tags")?.strings()?.distinctBy(String::lowercase) ?: emptyList(),
        )
    }

    private fun JSONArray.strings(): List<String> = List(length()) { index ->
        getString(index).trim().also { require(it.isNotEmpty()) }
    }
}
