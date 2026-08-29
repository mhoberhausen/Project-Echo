package com.mobileobie.echo.interpretation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAiLanTranscriptInterpreterDeviceTest {
    @Test
    fun processesSyntheticTranscriptThroughConfiguredLanServer() = runBlocking {
        val endpoint = InstrumentationRegistry.getArguments().getString("lanEndpoint")
        assumeTrue("Pass lanEndpoint to run the LAN integration test.", !endpoint.isNullOrBlank())
        val result = OpenAiLanTranscriptInterpreter(endpoint!!).interpret(
            "This is a local connector test. The color is blue. There are no tasks or dates."
        )

        assertTrue(result.summary.isNotBlank())
        assertTrue(result.actionItems.isEmpty())
    }
}
