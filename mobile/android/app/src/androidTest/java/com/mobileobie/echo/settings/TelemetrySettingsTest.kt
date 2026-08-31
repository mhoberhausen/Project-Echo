package com.mobileobie.echo.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TelemetrySettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("telemetry_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun consentDefaultsOffAndPersistsIndependently() {
        val settings = TelemetrySettings(context)
        assertEquals(TelemetryConsent(), settings.consent)

        settings.consent = TelemetryConsent(crashReportsEnabled = true)
        assertEquals(TelemetryConsent(crashReportsEnabled = true), TelemetrySettings(context).consent)
    }
}
