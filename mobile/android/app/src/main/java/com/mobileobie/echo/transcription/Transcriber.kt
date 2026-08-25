package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.model.TranscriptionModel

interface Transcriber {
    suspend fun transcribe(audio: RecordedAudio, model: TranscriptionModel): String
}
