package com.mobileobie.echo.external.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HuhAudioFrameReaderTest {
    @Test
    fun parsesHelloAcrossFragmentedReads() {
        val payload = payload {
            string("device-123")
            string("Kitchen recorder")
            string("Acme")
            string("Recorder One")
            string("1.2.3")
            writeInt(16_000)
            writeByte(1)
            writeByte(16)
            writeShort(320)
        }
        val input = FragmentedInputStream(ByteArrayInputStream(frame(HuhMessageType.HELLO, payload)), 3)

        val hello = HuhAudioFrameReader(input).readMessage() as HuhAudioMessage.Hello

        assertEquals("device-123", hello.deviceId)
        assertEquals("Kitchen recorder", hello.displayName)
        assertEquals(16_000, hello.sampleRateHz)
        assertEquals(320, hello.frameSamples)
    }

    @Test
    fun parsesLittleEndianAudioSamples() {
        val streamId = UUID.randomUUID()
        val pcm = ShortArray(320) { index -> (index - 160).toShort() }
        val payload = ByteBuffer.allocate(16 + 8 + 8 + 640).order(ByteOrder.BIG_ENDIAN)
            .putLong(streamId.mostSignificantBits)
            .putLong(streamId.leastSignificantBits)
            .putLong(7)
            .putLong(2_240)
            .apply {
                val bytes = ByteBuffer.allocate(640).order(ByteOrder.LITTLE_ENDIAN)
                pcm.forEach(bytes::putShort)
                put(bytes.array())
            }.array()

        val audio = HuhAudioFrameReader(ByteArrayInputStream(frame(HuhMessageType.AUDIO, payload)))
            .readMessage() as HuhAudioMessage.Audio

        assertEquals(streamId, audio.streamId)
        assertEquals(7, audio.sequence)
        assertEquals(2_240, audio.firstSampleIndex)
        assertEquals(pcm.toList(), audio.pcm.toList())
    }

    @Test
    fun rejectsOversizedPayloadBeforeAllocation() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).apply {
                write(HuhAudioProtocol.MAGIC)
                writeByte(HuhAudioProtocol.VERSION)
                writeByte(HuhMessageType.AUDIO.wireValue)
                writeShort(0)
                writeInt(HuhAudioProtocol.MAX_PAYLOAD_BYTES + 1)
            }
        }.toByteArray()

        assertThrows(ProtocolException::class.java) {
            HuhAudioFrameReader(ByteArrayInputStream(bytes)).readMessage()
        }
    }

    private fun frame(type: HuhMessageType, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).apply {
                write(HuhAudioProtocol.MAGIC)
                writeByte(HuhAudioProtocol.VERSION)
                writeByte(type.wireValue)
                writeShort(0)
                writeInt(payload.size)
                write(payload)
            }
        }.toByteArray()

    private fun payload(block: PayloadWriter.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { output ->
            PayloadWriter(DataOutputStream(output)).apply(block)
        }.toByteArray()

    private class PayloadWriter(private val output: DataOutputStream) {
        fun string(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            output.writeShort(bytes.size)
            output.write(bytes)
        }
        fun writeInt(value: Int) = output.writeInt(value)
        fun writeByte(value: Int) = output.writeByte(value)
        fun writeShort(value: Int) = output.writeShort(value)
    }

    private class FragmentedInputStream(input: ByteArrayInputStream, private val maxRead: Int) :
        FilterInputStream(input) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, minOf(length, maxRead))
    }
}
