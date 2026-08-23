package com.huh.app.transcription

import com.huh.app.audio.RecordedAudio
import com.huh.app.model.TranscriptionModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the native Whisper runtime single-jobbed across manual and active capture. */
class SerializedTranscriber(private val delegate: Transcriber) : Transcriber {
    private val mutex = Mutex()

    override suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel): String =
        mutex.withLock { delegate.transcribe(audio, model) }
}
