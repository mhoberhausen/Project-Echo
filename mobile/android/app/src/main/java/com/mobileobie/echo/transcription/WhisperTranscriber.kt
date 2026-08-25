package com.mobileobie.echo.transcription

import android.content.Context
import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.model.TranscriptionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Boundary for the bundled whisper.cpp engine. No Android or cloud speech API is
 * used. Until libwhisper_jni.so is packaged, this returns a clear setup error.
 */
class WhisperTranscriber(private val context: Context) : Transcriber {
    override suspend fun transcribe(
        audio: RecordedAudio,
        model: TranscriptionModel,
    ): String = withContext(Dispatchers.Default) {
        require(audio.sampleRateHz == 16_000) { "Whisper requires 16 kHz audio." }
        require(audio.samples.isNotEmpty()) { "No speech was recorded." }
        check(NativeWhisper.isAvailable) {
            "The offline Whisper engine is not installed in this build. See README.md for setup."
        }
        val modelFile = copyModelToFiles(model)
        NativeWhisper.transcribe(modelFile.absolutePath, audio.samples)
            .trim()
            .also { if (it.isEmpty()) throw NoSpeechDetectedException() }
    }

    private fun copyModelToFiles(model: TranscriptionModel): File {
        val destination = File(context.filesDir, model.assetFileName)
        if (!destination.exists()) {
            destination.parentFile?.mkdirs()
            context.assets.open("models/${model.assetFileName}").use { input ->
                destination.outputStream().use(input::copyTo)
            }
        }
        return destination
    }

}

class NoSpeechDetectedException : IllegalStateException("Whisper did not detect any speech.")

private object NativeWhisper {
    val isAvailable: Boolean = runCatching {
        System.loadLibrary("whisper_jni")
        true
    }.getOrDefault(false)

    external fun transcribe(modelPath: String, samples: ShortArray): String
}
