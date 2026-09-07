package com.mobileobie.echo.external

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PuckBleTestSoundClientTest {
    @Test
    fun commandPayloadUsesTheFirmwareControlEnvelope() {
        val requestId = 0x1234_5678
        val payload = PuckBleTestSoundClient.commandPayload(requestId)

        assertArrayEquals("HHC1".toByteArray(), payload.copyOfRange(0, 4))
        assertEquals(requestId, ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals("PLAY_TEST_SOUND", payload.copyOfRange(8, payload.size).decodeToString())
    }

    @Test
    fun responseRequiresVersionRequestIdAndResult() {
        assertEquals(42 to "OK", PuckBleTestSoundClient.parseResponse("v1|42|OK".toByteArray()))
        assertNull(PuckBleTestSoundClient.parseResponse("v2|42|OK".toByteArray()))
        assertNull(PuckBleTestSoundClient.parseResponse("v1|not-a-number|OK".toByteArray()))
    }
}
