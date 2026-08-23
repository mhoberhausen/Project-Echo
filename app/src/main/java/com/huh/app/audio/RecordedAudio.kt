package com.huh.app.audio

data class RecordedAudio(
    val samples: ShortArray,
    val sampleRateHz: Int,
)
