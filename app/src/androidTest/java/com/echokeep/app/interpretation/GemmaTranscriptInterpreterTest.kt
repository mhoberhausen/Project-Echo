package com.echokeep.app.interpretation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.echokeep.app.model.MessageIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaTranscriptInterpreterTest {
    @Test
    fun interpretsTranscriptOnDevice() = runBlocking {
        val interpreter = GemmaTranscriptInterpreter(ApplicationProvider.getApplicationContext())
        try {
            val result = interpreter.interpret(
                "Remind me to buy milk tomorrow at five PM, and call the dentist."
            )
            assertTrue(result.summary.isNotBlank())
            assertTrue(result.intent != MessageIntent.UNKNOWN)
        } finally {
            interpreter.release()
        }
    }
}
