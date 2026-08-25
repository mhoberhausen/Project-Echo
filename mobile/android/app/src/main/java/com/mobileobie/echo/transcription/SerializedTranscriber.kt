package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.model.TranscriptionModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the native Whisper runtime single-jobbed across manual and active capture. */
class SerializedTranscriber(private val delegate: Transcriber) : Transcriber {
    private val mutex = Mutex()

    override suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel): String =
        mutex.withLock { delegate.transcribe(audio, model) }
}
