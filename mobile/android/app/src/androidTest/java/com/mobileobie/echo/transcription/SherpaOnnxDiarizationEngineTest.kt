package com.mobileobie.echo.transcription

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobileobie.echo.audio.RecordedAudio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaOnnxDiarizationEngineTest {
    @Test(timeout = 120_000)
    fun bundledRuntimeLoadsModelsAndProcessesPcm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SherpaOnnxDiarizationEngine(context)
        assumeTrue("Optional sherpa-onnx artifacts are not installed.", engine.isInstalled())

        val turns = engine.detect(RecordedAudio(ShortArray(16_000 * 12), 16_000))

        assertTrue(turns.all { turn ->
            turn.startMillis >= 0 &&
                turn.endMillis >= turn.startMillis &&
                turn.speakerId.isNotBlank()
        })
    }
}
