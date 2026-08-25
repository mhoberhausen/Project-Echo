package com.mobileobie.echo.vad

fun interface VoiceActivityDetector {
    fun process(audio: ShortArray): VoiceActivity
}

enum class VoiceActivity {
    SPEECH,
    SILENCE,
}
