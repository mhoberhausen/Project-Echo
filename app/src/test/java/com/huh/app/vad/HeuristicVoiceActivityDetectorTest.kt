package com.huh.app.vad

import org.junit.Assert.assertEquals
import org.junit.Test

class HeuristicVoiceActivityDetectorTest {
    @Test
    fun classifiesSilenceAsSilence() {
        val detector = HeuristicVoiceActivityDetector()

        assertEquals(VoiceActivity.SILENCE, detector.process(ShortArray(320)))
    }

    @Test
    fun rejectsLoudSteadyToneDespiteAmplitude() {
        val detector = HeuristicVoiceActivityDetector()
        val steadyTone = ShortArray(320) { index -> if ((index / 8) % 2 == 0) 4_000 else (-4_000) }

        assertEquals(VoiceActivity.SILENCE, detector.process(steadyTone))
    }

    @Test
    fun acceptsModulatedVoiceLikeSignal() {
        val detector = HeuristicVoiceActivityDetector()
        val amplitudes = intArrayOf(800, 2_500, 1_000, 4_000, 1_200, 3_200, 900, 2_800)
        val voiceLike = ShortArray(320) { index ->
            val amplitude = amplitudes[index / 40]
            if ((index / 5) % 2 == 0) amplitude.toShort() else (-amplitude).toShort()
        }

        assertEquals(VoiceActivity.SPEECH, detector.process(voiceLike))
    }
}
