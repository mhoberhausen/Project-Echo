package com.echokeep.app.audio

interface AudioRecorder {
    suspend fun start()
    suspend fun stop(): RecordedAudio
    fun release()
}
