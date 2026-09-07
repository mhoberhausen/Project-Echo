package com.mobileobie.echo.external

import com.mobileobie.echo.audio.AudioRecorder
import com.mobileobie.echo.audio.PcmSampleBuffer
import com.mobileobie.echo.audio.RecordedAudio
import com.mobileobie.echo.external.protocol.AudioSequenceTracker
import com.mobileobie.echo.external.protocol.HuhAudioFrameReader
import com.mobileobie.echo.external.protocol.HuhAudioFrameWriter
import com.mobileobie.echo.external.protocol.HuhAudioMessage
import com.mobileobie.echo.external.protocol.ProtocolException
import com.mobileobie.echo.model.ExternalDeviceSessionMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** User-initiated, foreground-only HUH1 capture from a configured Puck on the local LAN. */
class PuckAudioRecorder(private val endpoint: ExternalDeviceEndpoint) : AudioRecorder {
    private val lock = Any()
    private var socket: Socket? = null
    private var readerJob: Job? = null
    private var streamId: UUID? = null
    private var samples = PcmSampleBuffer()
    var metadata: ExternalDeviceSessionMetadata? = null
        private set

    override suspend fun start() {
        check(socket == null) { "A Puck recording is already in progress." }
        val connected = Socket()
        connected.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
        connected.soTimeout = READ_TIMEOUT_MS
        try {
            val reader = HuhAudioFrameReader(connected.getInputStream())
            val hello = reader.readMessage() as? HuhAudioMessage.Hello
                ?: throw ProtocolException("The Huh? Puck did not send its identity.")
            validateHello(hello)
            metadata = ExternalDeviceSessionMetadata(
                deviceId = hello.deviceId,
                deviceName = hello.displayName.ifBlank { endpoint.displayName },
                manufacturer = hello.manufacturer,
                model = hello.model,
                firmwareVersion = hello.firmwareVersion,
                protocolVersion = 1,
                transport = "local_lan",
            )
            val id = UUID.randomUUID()
            HuhAudioFrameWriter(connected.getOutputStream()).start(id)
            synchronized(lock) { samples = PcmSampleBuffer() }
            socket = connected
            streamId = id
            readerJob = CoroutineScope(Dispatchers.IO).launch {
                val sequence = AudioSequenceTracker().apply { start(id) }
                while (true) when (val message = reader.readMessage() ?: break) {
                    is HuhAudioMessage.Audio -> sequence.accept(message).frames.forEach { frame ->
                        synchronized(lock) { samples.write(frame) }
                    }
                    is HuhAudioMessage.Stop -> break
                    is HuhAudioMessage.Error -> throw IllegalStateException("Huh? Puck: ${message.message}")
                    else -> Unit
                }
            }
        } catch (error: Throwable) {
            connected.close()
            throw error
        }
    }

    override suspend fun stop(): RecordedAudio {
        val current = checkNotNull(socket) { "No Puck recording is in progress." }
        streamId?.let { runCatching { HuhAudioFrameWriter(current.getOutputStream()).stop(it, com.mobileobie.echo.external.protocol.StreamEndReason.USER_STOP) } }
        current.close()
        readerJob?.cancelAndJoin()
        readerJob = null
        socket = null
        streamId = null
        return RecordedAudio(synchronized(lock) { samples.take() }, SAMPLE_RATE_HZ)
    }

    override fun release() { socket?.close(); socket = null; readerJob?.cancel(); readerJob = null }

    private fun validateHello(hello: HuhAudioMessage.Hello) {
        require(hello.sampleRateHz == SAMPLE_RATE_HZ && hello.channels == 1 && hello.sampleWidthBits == 16 && hello.frameSamples == 320) {
            "This Huh? Puck uses an unsupported audio format."
        }
        require(endpoint.expectedDeviceId.isBlank() || endpoint.expectedDeviceId == hello.deviceId) {
            "The connected Huh? Puck does not match the configured device ID."
        }
    }

    private companion object { const val SAMPLE_RATE_HZ = 16_000; const val CONNECT_TIMEOUT_MS = 8_000; const val READ_TIMEOUT_MS = 5_000 }
}
