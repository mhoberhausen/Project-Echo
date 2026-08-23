package com.huh.app.audio

interface AudioRecorder {
    suspend fun start()
    suspend fun stop(): RecordedAudio
    fun release()
}
