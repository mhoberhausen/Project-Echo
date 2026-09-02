package com.mobileobie.echo.model

enum class TranscriptionModel(
    val displayName: String,
    val description: String,
    val assetFileName: String,
) {
    ACCURATE(
        displayName = "Accurate",
        description = "base.en",
        assetFileName = "ggml-base.en.bin",
    ),
}
