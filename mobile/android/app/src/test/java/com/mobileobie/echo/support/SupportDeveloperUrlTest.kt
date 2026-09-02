package com.mobileobie.echo.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportDeveloperUrlTest {
    @Test
    fun acceptsConfiguredPublicHttpsUrl() {
        assertEquals(
            "https://example.org/support",
            SupportDeveloperUrl.configuredUrlOrNull(" https://example.org/support "),
        )
    }

    @Test
    fun rejectsMissingOrUnsafeSupportUrl() {
        assertNull(SupportDeveloperUrl.configuredUrlOrNull(SupportDeveloperUrl.SUPPORT_DEVELOPER_URL))
        assertNull(SupportDeveloperUrl.configuredUrlOrNull("http://example.org/support"))
        assertNull(SupportDeveloperUrl.configuredUrlOrNull("not a url"))
    }
}
