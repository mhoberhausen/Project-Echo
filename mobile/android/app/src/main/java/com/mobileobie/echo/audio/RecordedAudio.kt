package com.mobileobie.echo.audio

data class RecordedAudio(
    val samples: ShortArray,
    val sampleRateHz: Int,
)
