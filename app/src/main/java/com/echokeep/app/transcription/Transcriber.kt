package com.echokeep.app.transcription

import com.echokeep.app.audio.RecordedAudio

interface Transcriber {
    suspend fun transcribe(audio: RecordedAudio): String
}
