package com.mobileobie.echo.audio

interface AudioRecorder {
    suspend fun start()
    suspend fun stop(): RecordedAudio
    fun release()
}
