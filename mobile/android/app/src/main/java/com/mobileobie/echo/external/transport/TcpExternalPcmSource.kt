package com.mobileobie.echo.external.transport

import com.mobileobie.echo.audio.PcmSourceDescriptor
import com.mobileobie.echo.audio.PcmSourceDiagnostics
import com.mobileobie.echo.audio.PcmSourceEndReason
import com.mobileobie.echo.audio.PcmSourceEvent
import com.mobileobie.echo.audio.StreamingPcmSource
import com.mobileobie.echo.external.protocol.AudioSequenceTracker
import com.mobileobie.echo.external.protocol.HuhAudioFrameReader
import com.mobileobie.echo.external.protocol.HuhAudioMessage
import com.mobileobie.echo.external.protocol.ProtocolException
import com.mobileobie.echo.model.SessionSource
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receives canonical Huh? protocol audio from any compatible local device. */
class TcpExternalPcmSource(
    private val host: String,
    private val port: Int,
    private val expectedDeviceId: String? = null,
    private val connectTimeoutMs: Int = 10_000,
) : StreamingPcmSource {
    @Volatile private var socket: Socket? = null
    @Volatile private var currentDescriptor = PcmSourceDescriptor(
        sessionSource = SessionSource.EXTERNAL_DEVICE,
        transport = "Local Wi-Fi",
    )
    override val descriptor: PcmSourceDescriptor get() = currentDescriptor
    @Volatile private var currentDiagnostics = PcmSourceDiagnostics()
    override val diagnostics: PcmSourceDiagnostics get() = currentDiagnostics

    override suspend fun capture(onEvent: (PcmSourceEvent) -> Unit) = coroutineScope {
        val messages = Channel<HuhAudioMessage>(capacity = RECEIVE_WINDOW_FRAMES)
        val connected = withContext(Dispatchers.IO) {
            Socket().also {
                it.tcpNoDelay = true
                it.soTimeout = HEARTBEAT_TIMEOUT_MS
                it.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket = it
            }
        }
        val readerJob = launch(Dispatchers.IO) {
            try {
                val reader = HuhAudioFrameReader(connected.getInputStream())
                while (isActive) {
                    val message = reader.readMessage() ?: break
                    if (messages.trySend(message).isFailure) {
                        currentDiagnostics = currentDiagnostics.copy(
                            receiverOverrunCount = currentDiagnostics.receiverOverrunCount + 1,
                            interrupted = true,
                        )
                        throw ReceiverOverrunException()
                    }
                }
            } finally {
                messages.close()
            }
        }

        var helloReceived = false
        var activeStream: UUID? = null
        val sequence = AudioSequenceTracker()
        try {
            for (message in messages) when (message) {
                is HuhAudioMessage.Hello -> {
                    if (helloReceived || activeStream != null) throw ProtocolException("Unexpected HELLO.")
                    validateHello(message)
                    currentDescriptor = PcmSourceDescriptor(
                        sessionSource = SessionSource.EXTERNAL_DEVICE,
                        deviceId = message.deviceId,
                        deviceName = message.displayName,
                        manufacturer = message.manufacturer.ifBlank { null },
                        model = message.model.ifBlank { null },
                        firmwareVersion = message.firmwareVersion,
                        protocolVersion = 1,
                        transport = "Local Wi-Fi",
                    )
                    helloReceived = true
                    onEvent(PcmSourceEvent.Ready(currentDescriptor))
                }
                is HuhAudioMessage.Start -> {
                    if (!helloReceived || activeStream != null) throw ProtocolException("Unexpected START.")
                    activeStream = message.streamId
                    sequence.start(message.streamId)
                    currentDiagnostics = PcmSourceDiagnostics(streamId = message.streamId.toString())
                    onEvent(PcmSourceEvent.StreamStarted(message.streamId.toString()))
                }
                is HuhAudioMessage.Audio -> {
                    if (message.streamId != activeStream) throw ProtocolException("AUDIO received outside its stream.")
                    val result = sequence.accept(message)
                    if (result.insertedGapFrames > 0) {
                        currentDiagnostics = currentDiagnostics.copy(
                            gapCount = currentDiagnostics.gapCount + 1,
                            insertedSilenceSamples = currentDiagnostics.insertedSilenceSamples +
                                result.insertedGapFrames * 320L,
                        )
                    }
                    result.frames.forEach { onEvent(PcmSourceEvent.Audio(it)) }
                }
                is HuhAudioMessage.Heartbeat -> {
                    if (message.streamId != activeStream) throw ProtocolException("HEARTBEAT belongs to another stream.")
                }
                is HuhAudioMessage.Stop -> {
                    if (message.streamId != activeStream) throw ProtocolException("STOP belongs to another stream.")
                    activeStream = null
                    onEvent(PcmSourceEvent.StreamStopped(message.reason.toSourceReason()))
                }
                is HuhAudioMessage.Error -> throw RemoteDeviceException(message.code, message.message)
            }
            if (activeStream != null) {
                currentDiagnostics = currentDiagnostics.copy(interrupted = true)
                onEvent(PcmSourceEvent.StreamStopped(PcmSourceEndReason.DISCONNECT))
            }
            onEvent(PcmSourceEvent.Disconnected(null))
        } catch (error: Throwable) {
            currentDiagnostics = currentDiagnostics.copy(interrupted = activeStream != null)
            if (activeStream != null) onEvent(PcmSourceEvent.StreamStopped(PcmSourceEndReason.DISCONNECT))
            onEvent(PcmSourceEvent.Disconnected(error.message))
            throw error
        } finally {
            readerJob.cancel()
            runCatching { connected.close() }
            socket = null
        }
    }

    override fun stop() {
        runCatching { socket?.close() }
    }

    private fun validateHello(hello: HuhAudioMessage.Hello) {
        if (hello.deviceId.isBlank()) throw ProtocolException("Device ID is required.")
        if (expectedDeviceId != null && hello.deviceId != expectedDeviceId) {
            throw ProtocolException("The connected device is not the selected device.")
        }
        if (hello.sampleRateHz != 16_000 || hello.channels != 1 ||
            hello.sampleWidthBits != 16 || hello.frameSamples != 320
        ) throw ProtocolException("The device's audio format is unsupported.")
    }

    companion object {
        private const val RECEIVE_WINDOW_FRAMES = 50 // one second of canonical audio
        private const val HEARTBEAT_TIMEOUT_MS = 15_000
    }
}

class RemoteDeviceException(val code: Int, message: String) : IllegalStateException(message)
class ReceiverOverrunException : IllegalStateException("The phone could not process device audio fast enough.")

private fun com.mobileobie.echo.external.protocol.StreamEndReason.toSourceReason() = when (this) {
    com.mobileobie.echo.external.protocol.StreamEndReason.USER_STOP -> PcmSourceEndReason.USER_STOP
    com.mobileobie.echo.external.protocol.StreamEndReason.PAUSE -> PcmSourceEndReason.PAUSE
    com.mobileobie.echo.external.protocol.StreamEndReason.DISCONNECT -> PcmSourceEndReason.DISCONNECT
    com.mobileobie.echo.external.protocol.StreamEndReason.STORAGE_FAILURE -> PcmSourceEndReason.STORAGE_FAILURE
    com.mobileobie.echo.external.protocol.StreamEndReason.DEVICE_REBOOT -> PcmSourceEndReason.DEVICE_REBOOT
    com.mobileobie.echo.external.protocol.StreamEndReason.CAPTURE_FAILURE -> PcmSourceEndReason.CAPTURE_FAILURE
    com.mobileobie.echo.external.protocol.StreamEndReason.UNKNOWN -> PcmSourceEndReason.UNKNOWN
}
