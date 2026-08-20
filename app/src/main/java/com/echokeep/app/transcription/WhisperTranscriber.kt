package com.echokeep.app.transcription

import android.content.Context
import com.echokeep.app.audio.RecordedAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Boundary for the bundled whisper.cpp engine. No Android or cloud speech API is
 * used. Until libwhisper_jni.so is packaged, this returns a clear setup error.
 */
class WhisperTranscriber(private val context: Context) : Transcriber {
    override suspend fun transcribe(audio: RecordedAudio): String = withContext(Dispatchers.Default) {
        require(audio.sampleRateHz == 16_000) { "Whisper requires 16 kHz audio." }
        require(audio.samples.isNotEmpty()) { "No speech was recorded." }
        check(NativeWhisper.isAvailable) {
            "The offline Whisper engine is not installed in this build. See README.md for setup."
        }
        val model = copyModelToFiles()
        NativeWhisper.transcribe(model.absolutePath, audio.samples)
            .trim()
            .also { check(it.isNotEmpty()) { "Whisper did not detect any speech." } }
    }

    private fun copyModelToFiles(): File {
        val destination = File(context.filesDir, MODEL_FILE)
        if (!destination.exists()) {
            destination.parentFile?.mkdirs()
            context.assets.open("models/$MODEL_FILE").use { input ->
                destination.outputStream().use(input::copyTo)
            }
        }
        return destination
    }

    private companion object {
        const val MODEL_FILE = "ggml-tiny.en.bin"
    }
}

private object NativeWhisper {
    val isAvailable: Boolean = runCatching {
        System.loadLibrary("whisper_jni")
        true
    }.getOrDefault(false)

    external fun transcribe(modelPath: String, samples: ShortArray): String
}
