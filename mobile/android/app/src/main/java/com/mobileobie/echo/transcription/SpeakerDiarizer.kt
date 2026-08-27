package com.mobileobie.echo.transcription

import com.mobileobie.echo.audio.RecordedAudio

/** Post-transcription boundary for assigning speaker labels to timestamped text. */
fun interface SpeakerDiarizer {
    suspend fun diarize(
        audio: RecordedAudio,
        transcript: TimestampedTranscript,
    ): TimestampedTranscript
}

/** Placeholder until an on-device diarization model is selected. */
object PassthroughSpeakerDiarizer : SpeakerDiarizer {
    override suspend fun diarize(
        audio: RecordedAudio,
        transcript: TimestampedTranscript,
    ): TimestampedTranscript = transcript
}
