package com.mobileobie.echo.settings

import android.content.Context
import androidx.core.content.edit

data class TelemetryConsent(
    val crashReportsEnabled: Boolean = false,
    val usageInsightsEnabled: Boolean = false,
)

/** Local-only consent state. Both forms of reporting deliberately default to off. */
class TelemetrySettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var consent: TelemetryConsent
        get() = TelemetryConsent(
            crashReportsEnabled = preferences.getBoolean(KEY_CRASH_REPORTS, false),
            usageInsightsEnabled = preferences.getBoolean(KEY_USAGE_INSIGHTS, false),
        )
        set(value) {
            preferences.edit {
                putBoolean(KEY_CRASH_REPORTS, value.crashReportsEnabled)
                putBoolean(KEY_USAGE_INSIGHTS, value.usageInsightsEnabled)
            }
        }

    private companion object {
        const val PREFERENCES = "telemetry_settings"
        const val KEY_CRASH_REPORTS = "crash_reports_enabled"
        const val KEY_USAGE_INSIGHTS = "usage_insights_enabled"
    }
}
