package com.mobileobie.echo.vad

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Replaceable offline MVP detector combining adaptive energy, peak, zero-crossing, crest,
 * and short-window energy modulation. It intentionally does not classify on amplitude alone.
 */
class HeuristicVoiceActivityDetector(
    private val config: Config = Config(),
) : VoiceActivityDetector {
    data class Config(
        val minimumRms: Double = 350.0,
        val minimumPeak: Int = 800,
        val noiseFloorMultiplier: Double = 1.8,
        val minimumZeroCrossingRate: Double = 0.01,
        val maximumZeroCrossingRate: Double = 0.42,
        val minimumCrestFactor: Double = 1.45,
        val minimumEnergyModulation: Double = 0.05,
        val noiseFloorSmoothing: Double = 0.05,
        val analysisWindows: Int = 8,
        val speechHangoverFrames: Int = 10,
    ) {
        init {
            require(minimumRms >= 0.0)
            require(minimumPeak >= 0)
            require(noiseFloorMultiplier >= 1.0)
            require(minimumZeroCrossingRate in 0.0..1.0)
            require(maximumZeroCrossingRate in minimumZeroCrossingRate..1.0)
            require(minimumCrestFactor >= 1.0)
            require(minimumEnergyModulation >= 0.0)
            require(noiseFloorSmoothing in 0.0..1.0)
            require(analysisWindows >= 2)
            require(speechHangoverFrames >= 0)
        }
    }

    data class Diagnostics(
        val rms: Double = 0.0,
        val peak: Int = 0,
        val zeroCrossingRate: Double = 0.0,
        val crestFactor: Double = 0.0,
        val energyModulation: Double = 0.0,
        val estimatedNoiseRms: Double = 0.0,
        val energyThreshold: Double = 0.0,
        val rawSpeech: Boolean = false,
        val activity: VoiceActivity = VoiceActivity.SILENCE,
    )

    private var estimatedNoiseRms = config.minimumRms
    private var speechHangoverFramesRemaining = 0

    var diagnostics = Diagnostics(estimatedNoiseRms = estimatedNoiseRms)
        private set

    override fun process(audio: ShortArray): VoiceActivity {
        if (audio.size < config.analysisWindows) return VoiceActivity.SILENCE

        var sumSquares = 0.0
        var peak = 0
        var crossings = 0
        for (index in audio.indices) {
            val sample = audio[index].toInt()
            sumSquares += sample.toDouble() * sample
            peak = max(peak, abs(sample))
            if (index > 0 && (audio[index - 1] >= 0) != (audio[index] >= 0)) crossings++
        }

        val rms = sqrt(sumSquares / audio.size)
        val zeroCrossingRate = crossings.toDouble() / (audio.size - 1)
        val crestFactor = if (rms == 0.0) 0.0 else peak / rms
        val modulation = energyModulation(audio, rms)
        val energyThreshold = max(config.minimumRms, estimatedNoiseRms * config.noiseFloorMultiplier)

        val rawSpeech = rms >= energyThreshold &&
            peak >= config.minimumPeak &&
            zeroCrossingRate in config.minimumZeroCrossingRate..config.maximumZeroCrossingRate &&
            (crestFactor >= config.minimumCrestFactor || modulation >= config.minimumEnergyModulation)

        val activity = when {
            rawSpeech -> {
                speechHangoverFramesRemaining = config.speechHangoverFrames
                VoiceActivity.SPEECH
            }
            speechHangoverFramesRemaining > 0 -> {
                speechHangoverFramesRemaining--
                VoiceActivity.SPEECH
            }
            else -> VoiceActivity.SILENCE
        }

        diagnostics = Diagnostics(
            rms = rms,
            peak = peak,
            zeroCrossingRate = zeroCrossingRate,
            crestFactor = crestFactor,
            energyModulation = modulation,
            estimatedNoiseRms = estimatedNoiseRms,
            energyThreshold = energyThreshold,
            rawSpeech = rawSpeech,
            activity = activity,
        )

        if (activity == VoiceActivity.SILENCE) {
            val cappedNoiseSample = minOf(rms, max(config.minimumRms, estimatedNoiseRms * 1.1))
            estimatedNoiseRms += config.noiseFloorSmoothing * (cappedNoiseSample - estimatedNoiseRms)
        }
        return activity
    }

    private fun energyModulation(audio: ShortArray, overallRms: Double): Double {
        if (overallRms == 0.0) return 0.0
        val windowSize = (audio.size / config.analysisWindows).coerceAtLeast(1)
        var deviationSum = 0.0
        var windows = 0
        var start = 0
        while (start < audio.size) {
            val end = (start + windowSize).coerceAtMost(audio.size)
            var squares = 0.0
            for (index in start until end) {
                val sample = audio[index].toDouble()
                squares += sample * sample
            }
            val windowRms = sqrt(squares / (end - start))
            deviationSum += abs(windowRms - overallRms)
            windows++
            start = end
        }
        return deviationSum / windows / overallRms
    }
}
