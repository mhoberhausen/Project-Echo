package com.mobileobie.echo.settings

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AudioInputChoice(val label: String) {
    PHONE("This phone"),
    BLUETOOTH("Bluetooth device"),
    XIAO("Huh? Puck"),
}

enum class AiProviderKind(val label: String) {
    ON_DEVICE("On device"),
    LAN("Local network"),
    THIRD_PARTY("Third party"),
}

data class AiProviderConfig(
    val id: String,
    val name: String,
    val kind: AiProviderKind,
    val endpoint: String = "",
    val enabled: Boolean = true,
    val available: Boolean = false,
) {
    companion object {
        val ON_DEVICE_GEMMA = AiProviderConfig(
            id = "on-device-gemma",
            name = "Gemma 3 1B",
            kind = AiProviderKind.ON_DEVICE,
            available = true,
        )

        fun added(name: String, kind: AiProviderKind, endpoint: String) = AiProviderConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            kind = kind,
            endpoint = endpoint.trim(),
            available = kind == AiProviderKind.LAN,
        )

        fun withInstalledConnector(config: AiProviderConfig) = config.copy(
            available = config.available || config.kind == AiProviderKind.LAN,
        )
    }
}

class SelectionSettings(context: Context) {
    private val preferences = context.getSharedPreferences("selection_settings", Context.MODE_PRIVATE)

    var audioInput: AudioInputChoice
        get() = runCatching {
            AudioInputChoice.valueOf(preferences.getString(KEY_AUDIO_INPUT, null).orEmpty())
        }.getOrDefault(AudioInputChoice.PHONE)
        set(value) { preferences.edit { putString(KEY_AUDIO_INPUT, value.name) } }

    var aiProviders: List<AiProviderConfig>
        get() = runCatching {
            val stored = JSONArray(preferences.getString(KEY_AI_PROVIDERS, "[]"))
            buildList {
                for (index in 0 until stored.length()) {
                    val item = stored.getJSONObject(index)
                    add(
                        AiProviderConfig(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            kind = AiProviderKind.valueOf(item.getString("kind")),
                            endpoint = item.optString("endpoint"),
                            enabled = item.optBoolean("enabled", true),
                            available = item.optBoolean("available", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).ifEmpty { listOf(AiProviderConfig.ON_DEVICE_GEMMA) }
            .map(AiProviderConfig::withInstalledConnector)
        set(value) {
            val encoded = JSONArray().apply {
                value.forEach { provider ->
                    put(JSONObject().apply {
                        put("id", provider.id)
                        put("name", provider.name)
                        put("kind", provider.kind.name)
                        put("endpoint", provider.endpoint)
                        put("enabled", provider.enabled)
                        put("available", provider.available)
                    })
                }
            }
            preferences.edit { putString(KEY_AI_PROVIDERS, encoded.toString()) }
        }

    private companion object {
        const val KEY_AUDIO_INPUT = "audio_input"
        const val KEY_AI_PROVIDERS = "ai_providers"
    }
}
