package com.mobileobie.echo.support

import java.net.URI

/**
 * Keeps the optional developer-support destination deliberately separate from app behavior.
 *
 * The value is supplied by the app resource and intentionally defaults to blank. Until a
 * public HTTPS destination is configured, the support action is unavailable.
 */
object SupportDeveloperUrl {
    const val SUPPORT_DEVELOPER_URL: String = ""

    fun configuredUrlOrNull(value: String): String? {
        val candidate = value.trim()
        if (candidate.isEmpty()) return null

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        return candidate.takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() }
    }
}
