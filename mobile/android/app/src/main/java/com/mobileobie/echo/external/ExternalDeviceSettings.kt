package com.mobileobie.echo.external

import android.content.Context
import androidx.core.content.edit

data class ExternalDeviceEndpoint(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val expectedDeviceId: String = "",
    val displayName: String = "Huh? Puck",
) {
    val isConfigured: Boolean get() = host.isNotBlank() && port in 1..65_535

    fun validated(): ExternalDeviceEndpoint {
        require(host.isNotBlank()) { "Enter the device's local IP address or host name." }
        require(port in 1..65_535) { "Port must be between 1 and 65535." }
        return copy(
            host = host.trim(),
            expectedDeviceId = expectedDeviceId.trim(),
            displayName = displayName.trim().ifBlank { "External device" },
        )
    }

    companion object { const val DEFAULT_PORT = 8_765 }
}

class ExternalDeviceSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var endpoint: ExternalDeviceEndpoint
        get() = ExternalDeviceEndpoint(
            host = preferences.getString(KEY_HOST, "").orEmpty(),
            port = preferences.getInt(KEY_PORT, ExternalDeviceEndpoint.DEFAULT_PORT),
            expectedDeviceId = preferences.getString(KEY_DEVICE_ID, "").orEmpty(),
            displayName = preferences.getString(KEY_DISPLAY_NAME, "Huh? Puck").orEmpty(),
        )
        set(value) {
            val valid = value.validated()
            preferences.edit {
                putString(KEY_HOST, valid.host)
                putInt(KEY_PORT, valid.port)
                putString(KEY_DEVICE_ID, valid.expectedDeviceId)
                putString(KEY_DISPLAY_NAME, valid.displayName)
            }
        }

    fun forget() = preferences.edit { clear() }

    companion object {
        private const val PREFERENCES = "external_device"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_DEVICE_ID = "expected_device_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
