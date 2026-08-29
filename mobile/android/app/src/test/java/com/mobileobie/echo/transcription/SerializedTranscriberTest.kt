package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.model.TranscriptionModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SerializedTranscriberTest {
    @Test
    fun permitsOnlyOneNativeTranscriptionAtATime() = runBlocking {
        val activeCalls = AtomicInteger()
        val maximumConcurrentCalls = AtomicInteger()
        val delegate = object : Transcriber {
            override suspend fun transcribe(
                audio: RecordedAudio,
                model: TranscriptionModel,
            ): TimestampedTranscript {
                val active = activeCalls.incrementAndGet()
                maximumConcurrentCalls.updateAndGet { maxOf(it, active) }
                delay(50)
                activeCalls.decrementAndGet()
                return TimestampedTranscript(emptyList())
            }
        }
        val serialized = SerializedTranscriber(delegate)
        val audio = RecordedAudio(shortArrayOf(1), 16_000)

        listOf(
            async { serialized.transcribe(audio, TranscriptionModel.FAST) },
            async { serialized.transcribe(audio, TranscriptionModel.FAST) },
            async { serialized.transcribe(audio, TranscriptionModel.FAST) },
        ).awaitAll()

        assertEquals(1, maximumConcurrentCalls.get())
    }
}
