package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.ProcessedMessage
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** OpenAI-compatible inference restricted to a user-selected private/local-network endpoint. */
class OpenAiLanTranscriptInterpreter(endpoint: String) : TranscriptInterpreter {
    private val baseUri = validatedBaseUri(endpoint)
    @Volatile private var selectedModel: String? = null

    override suspend fun interpret(transcript: String): ProcessedMessage = withContext(Dispatchers.IO) {
        require(transcript.isNotBlank()) { "There is no transcript to process." }
        val model = selectedModel ?: discoverModel().also { selectedModel = it }
        val request = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("max_tokens", 512)
            put("messages", JSONArray().apply {
                put(message("system", TranscriptInterpretationPrompt.SYSTEM_INSTRUCTION))
                put(message("user", TranscriptInterpretationPrompt.forTranscript(transcript)))
            })
        }
        val response = request("chat/completions", "POST", request.toString())
        val content = JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        GemmaResponseParser.parse(content).validatedAgainstTranscript(transcript)
    }

    private fun discoverModel(): String {
        val models = JSONObject(request("models", "GET"))
            .getJSONArray("data")
            .let { data -> List(data.length()) { data.getJSONObject(it).getString("id") } }
            .filterNot { id -> id.contains("embed", ignoreCase = true) }
        return models.minByOrNull(::modelRank)
            ?: error("The LAN server did not report a chat-capable model.")
    }

    private fun modelRank(id: String): Int = when {
        id.contains("instruct", ignoreCase = true) -> 0
        id.contains("gemma", ignoreCase = true) -> 1
        id.contains("coder", ignoreCase = true) -> 3
        else -> 2
    }

    private fun request(path: String, method: String, body: String? = null): String {
        val connection = URL("${baseUri.toString().trimEnd('/')}/$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("The LAN AI server returned HTTP $status.")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun message(role: String, content: String) = JSONObject().apply {
        put("role", role)
        put("content", content)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 60_000

        internal fun validatedBaseUri(endpoint: String): URI {
            val uri = runCatching { URI(endpoint.trim()) }
                .getOrElse { throw IllegalArgumentException("Enter a valid LAN AI endpoint URL.") }
            require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                "The LAN AI endpoint must use http or https."
            }
            require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "Enter a valid LAN AI endpoint URL."
            }
            val addresses = runCatching { InetAddress.getAllByName(uri.host).toList() }
                .getOrElse { throw IllegalArgumentException("The LAN AI host could not be resolved.") }
            require(addresses.isNotEmpty() && addresses.all(::isLocalAddress)) {
                "LAN AI endpoints must resolve only to private or local addresses."
            }
            return uri
        }

        private fun isLocalAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
                return true
            }
            val bytes = address.address
            return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        }
    }
}
