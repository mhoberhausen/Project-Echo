package com.mobileobie.echo.vad

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
        val voiceLike = voiceLikeSignal()

        assertEquals(VoiceActivity.SPEECH, detector.process(voiceLike))
    }

    @Test
    fun speechHangoverBridgesBriefClassifierGaps() {
        val detector = HeuristicVoiceActivityDetector()

        assertEquals(VoiceActivity.SPEECH, detector.process(voiceLikeSignal()))
        repeat(10) {
            assertEquals(VoiceActivity.SPEECH, detector.process(ShortArray(320)))
        }
        assertEquals(VoiceActivity.SILENCE, detector.process(ShortArray(320)))
    }

    @Test
    fun rejectedLoudFramesDoNotImmediatelyPoisonNoiseFloor() {
        val detector = HeuristicVoiceActivityDetector()
        val loudSteadyTone = ShortArray(320) { index -> if ((index / 8) % 2 == 0) 4_000 else (-4_000) }

        repeat(10) { assertEquals(VoiceActivity.SILENCE, detector.process(loudSteadyTone)) }

        assertEquals(VoiceActivity.SPEECH, detector.process(voiceLikeSignal()))
    }

    private fun voiceLikeSignal(): ShortArray {
        val amplitudes = intArrayOf(800, 2_500, 1_000, 4_000, 1_200, 3_200, 900, 2_800)
        return ShortArray(320) { index ->
            val amplitude = amplitudes[index / 40]
            if ((index / 5) % 2 == 0) amplitude.toShort() else (-amplitude).toShort()
        }
    }
}
