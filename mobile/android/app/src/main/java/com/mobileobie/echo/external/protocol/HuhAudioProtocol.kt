package com.mobileobie.echo.external.protocol

import java.io.DataInputStream
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
    HELLO(1), START(2), AUDIO(3), HEARTBEAT(4), STOP(5), ERROR(6);

    companion object {
        fun fromWire(value: Int) = entries.firstOrNull { it.wireValue == value }
            ?: throw ProtocolException("Unknown message type $value.")
    }
}

enum class StreamEndReason(val wireValue: Int) {
    USER_STOP(1), PAUSE(2), DISCONNECT(3), STORAGE_FAILURE(4), DEVICE_REBOOT(5), CAPTURE_FAILURE(6),
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
        }
        if (payload.hasRemaining()) throw ProtocolException("Unexpected trailing payload bytes.")
        return message
    }
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
private fun ByteBuffer.readUuid() = UUID(readLongRequired(), readLongRequired())

private fun ByteBuffer.readString(): String {
    val length = readUnsignedShort()
    requireRemaining(length)
    return ByteArray(length).also(::get).toString(Charsets.UTF_8)
}
