package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.MessageIntent
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiLanTranscriptInterpreterTest {
    @Test
    fun discoversInstructionModelAndProcessesTranscript() = withServer { server, baseUrl ->
        val requestBody = AtomicReference<String>()
        server.createContext("/v1/models") { exchange ->
            exchange.respond(
                """{"object":"list","data":[{"id":"embedding-model"},{"id":"coder-model"},{"id":"local-instruct"}]}"""
            )
        }
        server.createContext("/v1/chat/completions") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.respond(
                """{"choices":[{"message":{"role":"assistant","content":"{\"summary\":\"LAN result\",\"intent\":\"note\",\"key_points\":[],\"action_items\":[],\"tags\":[\"local\"]}"}}]}"""
            )
        }
        server.start()

        val result = runBlocking {
            OpenAiLanTranscriptInterpreter("$baseUrl/v1").interpret("A private local transcript")
        }

        assertEquals("LAN result", result.summary)
        assertEquals(MessageIntent.NOTE, result.intent)
        assertEquals(listOf("local"), result.tags)
        val sent = JSONObject(requestBody.get())
        assertEquals("local-instruct", sent.getString("model"))
        assertTrue(sent.getJSONArray("messages").getJSONObject(1).getString("content").contains("A private local transcript"))
    }

    @Test
    fun rejectsPublicNetworkEndpoint() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpenAiLanTranscriptInterpreter.validatedBaseUri("https://8.8.8.8/v1")
        }
        assertEquals("LAN AI endpoints must resolve only to private or local addresses.", error.message)
    }

    private fun withServer(block: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            block(server, "http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
