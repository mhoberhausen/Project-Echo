package com.huh.app.model

enum class TranscriptionModel(
    val displayName: String,
    val description: String,
    val assetFileName: String,
) {
    FAST(
        displayName = "Fast",
        description = "tiny.en",
        assetFileName = "ggml-tiny.en.bin",
    ),
    ACCURATE(
        displayName = "Accurate",
        description = "base.en",
        assetFileName = "ggml-base.en.bin",
    ),
}
