package com.huh.app.vad

fun interface VoiceActivityDetector {
    fun process(audio: ShortArray): VoiceActivity
}

enum class VoiceActivity {
    SPEECH,
    SILENCE,
}
