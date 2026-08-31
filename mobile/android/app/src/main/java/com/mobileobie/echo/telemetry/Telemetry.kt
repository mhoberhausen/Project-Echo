package com.mobileobie.echo.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mobileobie.echo.settings.TelemetryConsent

/**
 * The only permitted application telemetry vocabulary. Values are fixed categories,
 * never user-provided text, audio, transcript data, names, endpoints, or credentials.
 */
sealed interface TelemetryEvent {
    val eventName: String
    fun parameters(): Bundle

    data class Capture(val source: Source, val outcome: Outcome) : TelemetryEvent {
        override val eventName = "capture_outcome"
        override fun parameters() = Bundle().apply {
            putString("source", source.value)
            putString("outcome", outcome.value)
        }
    }

    data class Processing(val stage: Stage, val outcome: Outcome, val duration: DurationBucket) : TelemetryEvent {
        override val eventName = "processing_outcome"
        override fun parameters() = Bundle().apply {
            putString("stage", stage.value)
            putString("outcome", outcome.value)
            putString("duration_bucket", duration.value)
        }
    }

    data class Inference(val provider: Provider, val outcome: Outcome) : TelemetryEvent {
        override val eventName = "inference_outcome"
        override fun parameters() = Bundle().apply {
            putString("provider", provider.value)
            putString("outcome", outcome.value)
        }
    }

    enum class Source(val value: String) { PHONE("phone"), ACTIVE_LISTENING("active_listening"), PUCK("puck") }
    enum class Stage(val value: String) { TRANSCRIPTION("transcription"), DIARIZATION("diarization") }
    enum class Provider(val value: String) { ON_DEVICE("on_device"), LAN("lan"), UNAVAILABLE("unavailable") }
    enum class Outcome(val value: String) { STARTED("started"), COMPLETED("completed"), CANCELLED("cancelled"), FAILED("failed") }
    enum class DurationBucket(val value: String) { UNDER_30_SECONDS("under_30s"), UNDER_2_MINUTES("under_2m"), OVER_2_MINUTES("over_2m") }
}

interface Telemetry {
    fun applyConsent(consent: TelemetryConsent)
    fun record(event: TelemetryEvent)
    fun recordSanitizedFailure(code: String)
}

object NoOpTelemetry : Telemetry {
    override fun applyConsent(consent: TelemetryConsent) = Unit
    override fun record(event: TelemetryEvent) = Unit
    override fun recordSanitizedFailure(code: String) = Unit
}

class FirebaseTelemetry private constructor(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private var consent: TelemetryConsent,
) : Telemetry {
    override fun applyConsent(consent: TelemetryConsent) {
        val enablingCrashReports = consent.crashReportsEnabled && !this.consent.crashReportsEnabled
        if (enablingCrashReports) crashlytics.deleteUnsentReports()
        analytics.setAnalyticsCollectionEnabled(consent.usageInsightsEnabled)
        crashlytics.setCrashlyticsCollectionEnabled(consent.crashReportsEnabled)
        if (!consent.crashReportsEnabled) crashlytics.deleteUnsentReports()
        this.consent = consent
    }

    override fun record(event: TelemetryEvent) {
        if (consent.usageInsightsEnabled) analytics.logEvent(event.eventName, event.parameters())
    }

    override fun recordSanitizedFailure(code: String) {
        if (consent.crashReportsEnabled) crashlytics.recordException(IllegalStateException("huh_$code"))
    }

    companion object {
        fun create(context: Context, consent: TelemetryConsent): Telemetry {
            val app = FirebaseApp.initializeApp(context) ?: return NoOpTelemetry
            return FirebaseTelemetry(
                FirebaseAnalytics.getInstance(app.applicationContext),
                FirebaseCrashlytics.getInstance(),
                consent,
            ).also { it.applyConsent(consent) }
        }
    }
}
