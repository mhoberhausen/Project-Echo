package com.mobileobie.echo.external.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object HuhAudioProtocol {
    const val VERSION = 1
    const val HEADER_BYTES = 12
    const val MAX_PAYLOAD_BYTES = 65_536
    const val PCM_BYTES_PER_FRAME = 640
    val MAGIC = byteArrayOf('H'.code.toByte(), 'U'.code.toByte(), 'H'.code.toByte(), '1'.code.toByte())
}

enum class HuhMessageType(val wireValue: Int) {
    HELLO(1), START(2), AUDIO(3), HEARTBEAT(4), STOP(5), ERROR(6), ACK(7), FETCH(8),
    FILE_CHUNK(9), FILE_END(10);

    companion object {
        fun fromWire(value: Int) = entries.firstOrNull { it.wireValue == value }
            ?: throw ProtocolException("Unknown message type $value.")
    }
}

enum class StreamEndReason(val wireValue: Int) {
    USER_STOP(1), PAUSE(2), DISCONNECT(3), STORAGE_FAILURE(4), DEVICE_REBOOT(5), CAPTURE_FAILURE(6),
    CONTROL_LEASE_EXPIRED(7),
    UNKNOWN(255);

    companion object {
        fun fromWire(value: Int) = entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

sealed interface HuhAudioMessage {
    data class Hello(
        val deviceId: String,
        val displayName: String,
        val manufacturer: String,
        val model: String,
        val firmwareVersion: String,
        val sampleRateHz: Int,
        val channels: Int,
        val sampleWidthBits: Int,
        val frameSamples: Int,
    ) : HuhAudioMessage

    data class Start(val streamId: UUID) : HuhAudioMessage
    data class Audio(
        val streamId: UUID,
        val sequence: Long,
        val firstSampleIndex: Long,
        val pcm: ShortArray,
    ) : HuhAudioMessage
    data class Heartbeat(val streamId: UUID, val lastSequence: Long) : HuhAudioMessage
    data class Stop(val streamId: UUID, val reason: StreamEndReason) : HuhAudioMessage
    data class Error(val code: Int, val message: String) : HuhAudioMessage
    data class Ack(val streamId: UUID) : HuhAudioMessage
    data class Fetch(val streamId: UUID, val offset: Long) : HuhAudioMessage
    data class FileChunk(val streamId: UUID, val offset: Long, val pcmBytes: ByteArray) : HuhAudioMessage
    data class FileEnd(val streamId: UUID, val totalBytes: Long) : HuhAudioMessage
}

class HuhAudioFrameReader(input: InputStream) {
    private val input = DataInputStream(input)

    fun readMessage(): HuhAudioMessage? {
        val first = input.read()
        if (first < 0) return null
        val magic = byteArrayOf(first.toByte(), input.readRequired(), input.readRequired(), input.readRequired())
        if (!magic.contentEquals(HuhAudioProtocol.MAGIC)) throw ProtocolException("Invalid protocol magic.")
        val version = input.readUnsignedByte()
        if (version != HuhAudioProtocol.VERSION) throw ProtocolException("Unsupported protocol version $version.")
        val type = HuhMessageType.fromWire(input.readUnsignedByte())
        val flags = input.readUnsignedShort()
        if (flags != 0) throw ProtocolException("Unsupported protocol flags $flags.")
        val payloadLength = input.readInt()
        if (payloadLength < 0 || payloadLength > HuhAudioProtocol.MAX_PAYLOAD_BYTES) {
            throw ProtocolException("Invalid payload length $payloadLength.")
        }
        val payload = ByteArray(payloadLength)
        try {
            input.readFully(payload)
        } catch (_: EOFException) {
            throw ProtocolException("Message payload ended early.")
        }
        return decode(type, ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN))
    }

    private fun decode(type: HuhMessageType, payload: ByteBuffer): HuhAudioMessage {
        val message = when (type) {
            HuhMessageType.HELLO -> HuhAudioMessage.Hello(
                deviceId = payload.readString(),
                displayName = payload.readString(),
                manufacturer = payload.readString(),
                model = payload.readString(),
                firmwareVersion = payload.readString(),
                sampleRateHz = payload.readIntRequired(),
                channels = payload.readUnsignedByte(),
                sampleWidthBits = payload.readUnsignedByte(),
                frameSamples = payload.readUnsignedShort(),
            )
            HuhMessageType.START -> HuhAudioMessage.Start(payload.readUuid())
            HuhMessageType.AUDIO -> {
                val streamId = payload.readUuid()
                val sequence = payload.readLongRequired()
                val firstSample = payload.readLongRequired()
                if (payload.remaining() != HuhAudioProtocol.PCM_BYTES_PER_FRAME) {
                    throw ProtocolException("AUDIO must contain exactly 640 PCM bytes.")
                }
                val pcmBytes = ByteArray(payload.remaining()).also(payload::get)
                val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                val pcm = ShortArray(pcmBytes.size / 2) { pcmBuffer.short }
                HuhAudioMessage.Audio(streamId, sequence, firstSample, pcm)
            }
            HuhMessageType.HEARTBEAT -> HuhAudioMessage.Heartbeat(payload.readUuid(), payload.readLongRequired())
            HuhMessageType.STOP -> HuhAudioMessage.Stop(payload.readUuid(), StreamEndReason.fromWire(payload.readUnsignedByte()))
            HuhMessageType.ERROR -> HuhAudioMessage.Error(payload.readUnsignedShort(), payload.readString())
            HuhMessageType.ACK -> HuhAudioMessage.Ack(payload.readUuid())
            HuhMessageType.FETCH -> HuhAudioMessage.Fetch(payload.readUuid(), payload.readUnsignedInt())
            HuhMessageType.FILE_CHUNK -> {
                val streamId = payload.readUuid()
                val offset = payload.readUnsignedInt()
                val bytes = ByteArray(payload.remaining()).also(payload::get)
                if (bytes.isEmpty()) throw ProtocolException("FILE_CHUNK cannot be empty.")
                HuhAudioMessage.FileChunk(streamId, offset, bytes)
            }
            HuhMessageType.FILE_END -> HuhAudioMessage.FileEnd(payload.readUuid(), payload.readUnsignedInt())
        }
        if (payload.hasRemaining()) throw ProtocolException("Unexpected trailing payload bytes.")
        return message
    }
}

class HuhAudioFrameWriter(output: java.io.OutputStream) {
    private val output = DataOutputStream(output)

    @Synchronized
    fun start(streamId: UUID) = write(HuhMessageType.START, uuidPayload(streamId))

    @Synchronized
    fun heartbeat(streamId: UUID, counter: Long) = write(
        HuhMessageType.HEARTBEAT,
        ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN).putUuid(streamId).putLong(counter).array(),
    )

    @Synchronized
    fun stop(streamId: UUID, reason: StreamEndReason) = write(
        HuhMessageType.STOP,
        ByteBuffer.allocate(17).order(ByteOrder.BIG_ENDIAN).putUuid(streamId)
            .put(reason.wireValue.toByte()).array(),
    )

    @Synchronized
    fun fetch(streamId: UUID, offset: Long) {
        require(offset in 0..UINT32_MAX) { "FETCH offset exceeds the protocol limit." }
        write(
            HuhMessageType.FETCH,
            ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN).putUuid(streamId)
                .putInt(offset.toInt()).array(),
        )
    }

    @Synchronized
    fun acknowledge(streamId: UUID) = write(HuhMessageType.ACK, uuidPayload(streamId))

    private fun write(type: HuhMessageType, payload: ByteArray) {
        require(payload.size <= HuhAudioProtocol.MAX_PAYLOAD_BYTES)
        output.write(HuhAudioProtocol.MAGIC)
        output.writeByte(HuhAudioProtocol.VERSION)
        output.writeByte(type.wireValue)
        output.writeShort(0)
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    private fun uuidPayload(streamId: UUID) =
        ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).putUuid(streamId).array()

    companion object { private const val UINT32_MAX = 0xffff_ffffL }
}

class ProtocolException(message: String) : IllegalArgumentException(message)

private fun DataInputStream.readRequired(): Byte {
    val value = read()
    if (value < 0) throw ProtocolException("Message header ended early.")
    return value.toByte()
}

private fun ByteBuffer.requireRemaining(count: Int) {
    if (remaining() < count) throw ProtocolException("Message payload ended early.")
}

private fun ByteBuffer.readUnsignedByte(): Int {
    requireRemaining(1)
    return get().toInt() and 0xff
}

private fun ByteBuffer.readUnsignedShort(): Int {
    requireRemaining(2)
    return short.toInt() and 0xffff
}

private fun ByteBuffer.readIntRequired(): Int { requireRemaining(4); return int }
private fun ByteBuffer.readLongRequired(): Long { requireRemaining(8); return long }
private fun ByteBuffer.readUnsignedInt(): Long = readIntRequired().toLong() and 0xffff_ffffL
private fun ByteBuffer.readUuid() = UUID(readLongRequired(), readLongRequired())
private fun ByteBuffer.putUuid(value: UUID): ByteBuffer =
    putLong(value.mostSignificantBits).putLong(value.leastSignificantBits)

private fun ByteBuffer.readString(): String {
    val length = readUnsignedShort()
    requireRemaining(length)
    return ByteArray(length).also(::get).toString(Charsets.UTF_8)
}
