/*
 * Adapted from sherpa-onnx v1.13.6, licensed under Apache License 2.0.
 * https://github.com/k2-fsa/sherpa-onnx
 */
package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

data class OfflineSpeakerSegmentationPyannoteModelConfig(
    var model: String = "",
    var windowShiftRatio: Float = 0.1f,
)

data class OfflineSpeakerSegmentationModelConfig(
    var pyannote: OfflineSpeakerSegmentationPyannoteModelConfig =
        OfflineSpeakerSegmentationPyannoteModelConfig(),
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
)

data class FastClusteringConfig(
    var numClusters: Int = -1,
    var threshold: Float = 0.5f,
)

data class OfflineSpeakerDiarizationConfig(
    var segmentation: OfflineSpeakerSegmentationModelConfig =
        OfflineSpeakerSegmentationModelConfig(),
    var embedding: SpeakerEmbeddingExtractorConfig = SpeakerEmbeddingExtractorConfig(),
    var clustering: FastClusteringConfig = FastClusteringConfig(),
    var minDurationOn: Float = 0.2f,
    var minDurationOff: Float = 0.5f,
)

data class OfflineSpeakerDiarizationSegment(
    val start: Float,
    val end: Float,
    val speaker: Int,
)

class OfflineSpeakerDiarization(
    assetManager: AssetManager? = null,
    val config: OfflineSpeakerDiarizationConfig,
) {
    private var pointer: Long = if (assetManager != null) {
        newFromAsset(assetManager, config)
    } else {
        newFromFile(config)
    }

    init {
        require(pointer != 0L) { "Failed to create the native offline speaker diarizer." }
    }

    fun release() {
        if (pointer != 0L) {
            delete(pointer)
            pointer = 0L
        }
    }

    fun sampleRate(): Int = getSampleRate(pointer)

    fun process(samples: FloatArray): Array<OfflineSpeakerDiarizationSegment> =
        process(pointer, samples)

    private external fun delete(pointer: Long)

    private external fun newFromAsset(
        assetManager: AssetManager,
        config: OfflineSpeakerDiarizationConfig,
    ): Long

    private external fun newFromFile(config: OfflineSpeakerDiarizationConfig): Long

    private external fun getSampleRate(pointer: Long): Int

    private external fun process(
        pointer: Long,
        samples: FloatArray,
    ): Array<OfflineSpeakerDiarizationSegment>

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
