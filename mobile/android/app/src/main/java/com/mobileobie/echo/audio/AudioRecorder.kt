package com.mobileobie.echo.audio

interface AudioRecorder {
    suspend fun start()
    suspend fun stop(): RecordedAudio
    fun release()
}

data class RecordedAudioChunk(
    val audio: RecordedAudio,
    val startMillis: Long,
)

interface IncrementalAudioRecorder {
    suspend fun startIncremental(
        quietBoundaryMs: Long,
        onChunk: (RecordedAudioChunk) -> Unit,
    )
}
