package com.mobileobie.echo.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryEventTest {
    @Test
    fun processingEventUsesAnApprovedFixedName() {
        val event = TelemetryEvent.Processing(
            TelemetryEvent.Stage.TRANSCRIPTION,
            TelemetryEvent.Outcome.COMPLETED,
            TelemetryEvent.DurationBucket.UNDER_2_MINUTES,
        )

        assertEquals("processing_outcome", event.eventName)
    }

    @Test
    fun noOpTelemetryAcceptsAllCallsWithoutCollecting() {
        NoOpTelemetry.applyConsent(com.mobileobie.echo.settings.TelemetryConsent(true, true))
        NoOpTelemetry.record(TelemetryEvent.Capture(TelemetryEvent.Source.PHONE, TelemetryEvent.Outcome.STARTED))
        NoOpTelemetry.recordSanitizedFailure("manual_transcription_failed")
    }
}
