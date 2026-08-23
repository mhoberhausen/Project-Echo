package com.huh.app.transcription

import com.huh.app.audio.RecordedAudio
import com.huh.app.model.TranscriptionModel

interface Transcriber {
    suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel): String
}
