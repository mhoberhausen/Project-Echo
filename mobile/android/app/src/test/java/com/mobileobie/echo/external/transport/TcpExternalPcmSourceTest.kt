package com.mobileobie.echo.external.transport

import com.mobileobie.echo.audio.PcmSourceEndReason
import com.mobileobie.echo.audio.PcmSourceEvent
import com.mobileobie.echo.external.protocol.HuhAudioFrameReader
import com.mobileobie.echo.external.protocol.HuhAudioMessage
import com.mobileobie.echo.external.protocol.HuhAudioProtocol
import com.mobileobie.echo.external.protocol.HuhMessageType
import com.mobileobie.echo.external.protocol.StreamEndReason
import java.io.DataOutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Collections
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpExternalPcmSourceTest {
    @Test
    fun controlsLeaseFetchesFinalizedPcmAndAcknowledgesBeforeReturning() {
        runBlocking {
        val server = ServerSocket(0)
        val transferDirectory = Files.createTempDirectory("huh-external-test").toFile()
        val pcm = ByteArray(1_280) { (it % 251).toByte() }
        val observed = Collections.synchronizedList(mutableListOf<HuhMessageType>())
        val firmware = thread(name = "fake-huh-firmware") {
            server.accept().use { socket ->
                val reader = HuhAudioFrameReader(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                output.writeFrame(HuhMessageType.HELLO, helloPayload())

                val start = reader.readMessage() as HuhAudioMessage.Start
                observed += HuhMessageType.START
                output.writeFrame(HuhMessageType.START, start.streamId.uuidBytes())

                var stop: HuhAudioMessage.Stop? = null
                while (stop == null) {
                    when (val command = reader.readMessage()) {
                        is HuhAudioMessage.Heartbeat -> observed += HuhMessageType.HEARTBEAT
                        is HuhAudioMessage.Stop -> stop = command
                        else -> error("Unexpected command $command")
                    }
                }
                observed += HuhMessageType.STOP
                output.writeFrame(
                    HuhMessageType.STOP,
                    start.streamId.uuidBytes() + byteArrayOf(StreamEndReason.PAUSE.wireValue.toByte()),
                )

                val fetch = reader.readMessage() as HuhAudioMessage.Fetch
                assertEquals(0L, fetch.offset)
                observed += HuhMessageType.FETCH
                output.writeFrame(
                    HuhMessageType.FILE_CHUNK,
                    start.streamId.uuidBytes() + uint32(0) + pcm.copyOfRange(0, 777),
                )
                output.writeFrame(
                    HuhMessageType.FILE_CHUNK,
                    start.streamId.uuidBytes() + uint32(777) + pcm.copyOfRange(777, pcm.size),
                )
                output.writeFrame(
                    HuhMessageType.FILE_END,
                    start.streamId.uuidBytes() + uint32(pcm.size),
                )
                val ack = reader.readMessage() as HuhAudioMessage.Ack
                assertEquals(start.streamId, ack.streamId)
                observed += HuhMessageType.ACK
            }
        }

        val events = Collections.synchronizedList(mutableListOf<PcmSourceEvent>())
        var finalizedPcm = ByteArray(0)
        val source = TcpExternalPcmSource(
            host = "127.0.0.1",
            port = server.localPort,
            transferDirectory = transferDirectory,
            expectedDeviceId = "device-123",
        )
        withTimeout(5_000) {
            source.capture { event ->
                events += event
                if (event is PcmSourceEvent.FinalizedAudio) {
                    finalizedPcm = event.file.readBytes()
                }
                // Exercise a user pause during the HELLO/START handshake as well as the
                // normal finalized-file transfer path.
                if (event is PcmSourceEvent.Ready) {
                    source.requestStop(PcmSourceEndReason.PAUSE)
                }
            }
        }
        firmware.join(2_000)
        server.close()

        assertEquals(0, events.filterIsInstance<PcmSourceEvent.Audio>().size)
        assertEquals(40L, events.filterIsInstance<PcmSourceEvent.FinalizedAudio>().single().durationMillis)
        assertTrue(pcm.contentEquals(finalizedPcm))
        assertEquals(
            PcmSourceEndReason.PAUSE,
            events.filterIsInstance<PcmSourceEvent.StreamStopped>().single().reason,
        )
        assertEquals(
            listOf(
                HuhMessageType.START,
                HuhMessageType.STOP,
                HuhMessageType.FETCH,
                HuhMessageType.ACK,
            ),
            observed.filterNot { it == HuhMessageType.HEARTBEAT },
        )
        assertTrue(transferDirectory.listFiles().orEmpty().isEmpty())
            transferDirectory.deleteRecursively()
        }
    }

    private fun helloPayload() = buildList<ByteArray> {
        add(string("device-123"))
        add(string("Huh? Puck"))
        add(string("Seeed Studio"))
        add(string("XIAO ESP32S3 Sense"))
        add(string("0.1.0"))
        add(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(16_000).put(1.toByte()).put(16.toByte()).putShort(320.toShort()).array())
    }.fold(ByteArray(0), ByteArray::plus)

    private fun string(value: String): ByteArray {
        val bytes = value.toByteArray()
        return ByteBuffer.allocate(2 + bytes.size).order(ByteOrder.BIG_ENDIAN)
            .putShort(bytes.size.toShort()).put(bytes).array()
    }

    private fun UUID.uuidBytes() = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        .putLong(mostSignificantBits).putLong(leastSignificantBits).array()

    private fun uint32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        .putInt(value).array()

    private fun DataOutputStream.writeFrame(type: HuhMessageType, payload: ByteArray) {
        write(HuhAudioProtocol.MAGIC)
        writeByte(HuhAudioProtocol.VERSION)
        writeByte(type.wireValue)
        writeShort(0)
        writeInt(payload.size)
        write(payload)
        flush()
    }
}
