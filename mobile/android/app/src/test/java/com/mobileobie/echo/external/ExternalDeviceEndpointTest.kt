package com.mobileobie.echo.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExternalDeviceEndpointTest {
    @Test
    fun normalizesValidManualEndpoint() {
        val endpoint = ExternalDeviceEndpoint(
            host = " 192.168.1.42 ", port = 8765, expectedDeviceId = " puck-1 ", displayName = " Kitchen ",
        ).validated()

        assertEquals("192.168.1.42", endpoint.host)
        assertEquals("puck-1", endpoint.expectedDeviceId)
        assertEquals("Kitchen", endpoint.displayName)
    }

    @Test
    fun rejectsMissingHostAndInvalidPort() {
        assertThrows(IllegalArgumentException::class.java) { ExternalDeviceEndpoint().validated() }
        assertThrows(IllegalArgumentException::class.java) {
            ExternalDeviceEndpoint(host = "device.local", port = 0).validated()
        }
    }
}
