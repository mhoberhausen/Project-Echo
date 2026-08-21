package com.echokeep.app.transcription

import com.echokeep.app.audio.RecordedAudio
import com.echokeep.app.model.TranscriptionModel

interface Transcriber {
    suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel): String
}
