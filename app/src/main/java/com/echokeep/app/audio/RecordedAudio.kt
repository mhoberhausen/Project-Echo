package com.echokeep.app.audio

data class RecordedAudio(
    val samples: ShortArray,
    val sampleRateHz: Int,
)
