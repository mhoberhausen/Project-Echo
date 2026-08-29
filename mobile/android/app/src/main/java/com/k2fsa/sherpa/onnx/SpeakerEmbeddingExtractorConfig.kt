/*
 * Adapted from sherpa-onnx v1.13.6, licensed under Apache License 2.0.
 * https://github.com/k2-fsa/sherpa-onnx
 */
package com.k2fsa.sherpa.onnx

data class SpeakerEmbeddingExtractorConfig(
    val model: String = "",
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
)
