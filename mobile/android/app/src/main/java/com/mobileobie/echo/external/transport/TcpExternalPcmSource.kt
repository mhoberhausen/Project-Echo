package com.mobileobie.echo.external.transport

import com.mobileobie.echo.audio.PcmSourceDescriptor
import com.mobileobie.echo.audio.PcmSourceDiagnostics
import com.mobileobie.echo.audio.PcmSourceEndReason
import com.mobileobie.echo.audio.PcmSourceEvent
import com.mobileobie.echo.audio.StreamingPcmSource
import com.mobileobie.echo.external.protocol.AudioSequenceTracker
import com.mobileobie.echo.external.protocol.HuhAudioFrameReader
import com.mobileobie.echo.external.protocol.HuhAudioFrameWriter
import com.mobileobie.echo.external.protocol.HuhAudioMessage
import com.mobileobie.echo.external.protocol.ProtocolException
import com.mobileobie.echo.external.protocol.StreamEndReason
import com.mobileobie.echo.model.SessionSource
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controls an SD-backed HUH1 capture and downloads it after the microphone has stopped.
 * No audio is exposed until FILE_END verifies that the complete recording is on Android.
 */
class TcpExternalPcmSource(
    private val host: String,
    private val port: Int,
    private val transferDirectory: File,
    private val expectedDeviceId: String? = null,
    private val connectTimeoutMs: Int = 10_000,
) : StreamingPcmSource {
    private val stateLock = Any()
    @Volatile private var socket: Socket? = null
    @Volatile private var frameWriter: HuhAudioFrameWriter? = null
    @Volatile private var leaseHeartbeatJob: Job? = null
    @Volatile private var streamAcknowledged = false
    @Volatile private var stopRequested = false
    @Volatile private var stopSent = false
    @Volatile private var requestedStopReason = StreamEndReason.USER_STOP
    private var pendingStreamId: UUID? = null
    private var pendingFile: File? = null
    private var receivedBytes = 0L
    private var remoteStopReason = StreamEndReason.UNKNOWN

    @Volatile private var currentDescriptor = PcmSourceDescriptor(
        sessionSource = SessionSource.EXTERNAL_DEVICE,
        transport = "Local Wi-Fi + microSD",
    )
    override val descriptor: PcmSourceDescriptor get() = currentDescriptor
    @Volatile private var currentDiagnostics = PcmSourceDiagnostics()
    override val diagnostics: PcmSourceDiagnostics get() = currentDiagnostics

    override suspend fun capture(onEvent: (PcmSourceEvent) -> Unit) = coroutineScope {
        val connected = withContext(Dispatchers.IO) {
            Socket().also {
                it.tcpNoDelay = true
                it.soTimeout = 0 // Capture may intentionally run for hours without inbound traffic.
                it.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket = it
            }
        }
        val writer = HuhAudioFrameWriter(connected.getOutputStream()).also { frameWriter = it }
        val reader = HuhAudioFrameReader(connected.getInputStream())
        var helloReceived = false
        var completed = false
        val legacySequence = AudioSequenceTracker()

        try {
            while (isActive && !completed) {
                val message = withContext(Dispatchers.IO) { reader.readMessage() }
                    ?: throw EOFException("The external device closed the connection.")
                when (message) {
                    is HuhAudioMessage.Hello -> {
                        if (helloReceived) throw ProtocolException("Unexpected duplicate HELLO.")
                        validateHello(message)
                        currentDescriptor = PcmSourceDescriptor(
                            sessionSource = SessionSource.EXTERNAL_DEVICE,
                            deviceId = message.deviceId,
                            deviceName = message.displayName,
                            manufacturer = message.manufacturer.ifBlank { null },
                            model = message.model.ifBlank { null },
                            firmwareVersion = message.firmwareVersion,
                            protocolVersion = 1,
                            transport = "Local Wi-Fi + microSD",
                        )
                        helloReceived = true
                        onEvent(PcmSourceEvent.Ready(currentDescriptor))
                        val pending = synchronized(stateLock) { pendingStreamId }
                        if (pending == null) beginNewCapture(writer) else writer.fetch(pending, receivedBytes)
                    }
                    is HuhAudioMessage.Start -> {
                        val expected = synchronized(stateLock) { pendingStreamId }
                        if (!helloReceived || message.streamId != expected) {
                            throw ProtocolException("START did not acknowledge this controller's capture.")
                        }
                        streamAcknowledged = true
                        legacySequence.start(message.streamId)
                        currentDiagnostics = PcmSourceDiagnostics(streamId = message.streamId.toString())
                        onEvent(PcmSourceEvent.StreamStarted(message.streamId.toString()))
                        if (stopRequested && !stopSent) {
                            connected.soTimeout = TRANSFER_TIMEOUT_MS
                            writer.stop(message.streamId, requestedStopReason)
                            stopSent = true
                        } else {
                            leaseHeartbeatJob = launch(Dispatchers.IO) {
                                var counter = 0L
                                while (isActive && !stopRequested) {
                                    writer.heartbeat(message.streamId, counter++)
                                    delay(HEARTBEAT_INTERVAL_MS)
                                }
                            }
                        }
                    }
                    is HuhAudioMessage.Stop -> {
                        requireOwnedStream(message.streamId, "STOP")
                        leaseHeartbeatJob?.cancel()
                        streamAcknowledged = false
                        remoteStopReason = message.reason
                        connected.soTimeout = TRANSFER_TIMEOUT_MS
                        writer.fetch(message.streamId, receivedBytes)
                    }
                    is HuhAudioMessage.FileChunk -> {
                        requireOwnedStream(message.streamId, "FILE_CHUNK")
                        if (message.offset != receivedBytes) {
                            throw ProtocolException(
                                "FILE_CHUNK offset ${message.offset} does not match $receivedBytes.",
                            )
                        }
                        appendTransfer(message.pcmBytes)
                    }
                    is HuhAudioMessage.FileEnd -> {
                        requireOwnedStream(message.streamId, "FILE_END")
                        if (message.totalBytes != receivedBytes) {
                            throw ProtocolException(
                                "FILE_END reports ${message.totalBytes} bytes; received $receivedBytes.",
                            )
                        }
                        val completedFile = finalizeTransfer()
                        onEvent(
                            PcmSourceEvent.FinalizedAudio(
                                file = completedFile,
                                durationMillis = receivedBytes * 1_000L / PCM_BYTES_PER_SECOND,
                            )
                        )
                        onEvent(PcmSourceEvent.StreamStopped(remoteStopReason.toSourceReason()))
                        writer.acknowledge(message.streamId)
                        completedFile.delete()
                        clearCompletedCapture()
                        completed = true
                    }
                    is HuhAudioMessage.Audio -> {
                        // Compatibility with pre-SD firmware during migration.
                        requireOwnedStream(message.streamId, "AUDIO")
                        legacySequence.accept(message).frames.forEach {
                            onEvent(PcmSourceEvent.Audio(it))
                        }
                    }
                    is HuhAudioMessage.Heartbeat -> requireOwnedStream(message.streamId, "HEARTBEAT")
                    is HuhAudioMessage.Error -> throw RemoteDeviceException(message.code, message.message)
                    is HuhAudioMessage.Ack,
                    is HuhAudioMessage.Fetch -> throw ProtocolException("Unexpected controller message from device.")
                }
            }
            onEvent(PcmSourceEvent.Disconnected(null))
        } catch (error: Throwable) {
            currentDiagnostics = currentDiagnostics.copy(interrupted = pendingStreamId != null)
            onEvent(PcmSourceEvent.Disconnected(error.message))
            throw error
        } finally {
            leaseHeartbeatJob?.cancel()
            leaseHeartbeatJob = null
            frameWriter = null
            socket = null
            runCatching { connected.close() }
        }
    }

    fun requestStop(reason: PcmSourceEndReason) {
        requestedStopReason = when (reason) {
            PcmSourceEndReason.PAUSE -> StreamEndReason.PAUSE
            else -> StreamEndReason.USER_STOP
        }
        stopRequested = true
        leaseHeartbeatJob?.cancel()
        val stream = synchronized(stateLock) { pendingStreamId }
        if (stream != null && streamAcknowledged) {
            runCatching {
                socket?.soTimeout = TRANSFER_TIMEOUT_MS
                frameWriter?.stop(stream, requestedStopReason)
                stopSent = true
            }.onFailure { runCatching { socket?.close() } }
        }
    }

    override fun stop() = requestStop(PcmSourceEndReason.USER_STOP)

    private fun beginNewCapture(writer: HuhAudioFrameWriter) {
        transferDirectory.mkdirs()
        check(transferDirectory.isDirectory) { "External-device transfer storage is unavailable." }
        val stream = UUID.randomUUID()
        val file = File.createTempFile("huh_external_", ".partial", transferDirectory)
        synchronized(stateLock) {
            pendingStreamId = stream
            pendingFile = file
            receivedBytes = 0L
            remoteStopReason = StreamEndReason.UNKNOWN
            stopSent = false
            streamAcknowledged = false
        }
        writer.start(stream)
    }

    private fun appendTransfer(bytes: ByteArray) {
        val file = checkNotNull(pendingFile) { "No local transfer file is active." }
        RandomAccessFile(file, "rw").use {
            it.seek(receivedBytes)
            it.write(bytes)
        }
        receivedBytes += bytes.size
    }

    private fun finalizeTransfer(): File {
        val partial = checkNotNull(pendingFile)
        if (receivedBytes % PCM_BYTES_PER_FRAME != 0L) {
            throw ProtocolException("Transferred PCM is not aligned to 20 ms frames.")
        }
        val completed = File(partial.parentFile, partial.name.removeSuffix(".partial") + ".pcm")
        check(partial.renameTo(completed)) { "External audio could not be finalized locally." }
        pendingFile = completed
        return completed
    }

    private fun clearCompletedCapture() = synchronized(stateLock) {
        pendingStreamId = null
        pendingFile = null
        receivedBytes = 0L
        remoteStopReason = StreamEndReason.UNKNOWN
        stopRequested = false
        stopSent = false
        streamAcknowledged = false
    }

    private fun requireOwnedStream(streamId: UUID, message: String) {
        if (streamId != synchronized(stateLock) { pendingStreamId }) {
            throw ProtocolException("$message belongs to another capture.")
        }
    }

    private fun validateHello(hello: HuhAudioMessage.Hello) {
        if (hello.deviceId.isBlank()) throw ProtocolException("Device ID is required.")
        if (expectedDeviceId != null && hello.deviceId != expectedDeviceId) {
            throw ProtocolException("The connected device is not the selected device.")
        }
        if (hello.sampleRateHz != 16_000 || hello.channels != 1 ||
            hello.sampleWidthBits != 16 || hello.frameSamples != FRAME_SAMPLES
        ) throw ProtocolException("The device's audio format is unsupported.")
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val TRANSFER_TIMEOUT_MS = 60_000
        private const val FRAME_SAMPLES = 320
        private const val PCM_BYTES_PER_FRAME = 640
        private const val PCM_BYTES_PER_SECOND = 16_000 * 2
    }
}

class RemoteDeviceException(val code: Int, message: String) : IllegalStateException(message)

private fun StreamEndReason.toSourceReason() = when (this) {
    StreamEndReason.USER_STOP -> PcmSourceEndReason.USER_STOP
    StreamEndReason.PAUSE -> PcmSourceEndReason.PAUSE
    StreamEndReason.DISCONNECT, StreamEndReason.CONTROL_LEASE_EXPIRED -> PcmSourceEndReason.DISCONNECT
    StreamEndReason.STORAGE_FAILURE -> PcmSourceEndReason.STORAGE_FAILURE
    StreamEndReason.DEVICE_REBOOT -> PcmSourceEndReason.DEVICE_REBOOT
    StreamEndReason.CAPTURE_FAILURE -> PcmSourceEndReason.CAPTURE_FAILURE
    StreamEndReason.UNKNOWN -> PcmSourceEndReason.UNKNOWN
}
