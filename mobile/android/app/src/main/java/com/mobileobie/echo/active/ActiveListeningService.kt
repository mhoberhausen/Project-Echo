package com.mobileobie.echo.active

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mobileobie.echo.HuhApplication
import com.mobileobie.echo.audio.RollingAudioBuffer
import com.mobileobie.echo.audio.StreamingAudioCapture
import com.mobileobie.echo.audio.StreamingPcmSource
import com.mobileobie.echo.audio.PcmSourceEndReason
import com.mobileobie.echo.audio.PcmSourceEvent
import com.mobileobie.echo.audio.AudioRouteChangedException
import com.mobileobie.echo.model.SessionMetadata
import com.mobileobie.echo.model.SessionSource
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.model.ExternalDeviceSessionMetadata
import com.mobileobie.echo.external.ExternalDeviceEndpoint
import com.mobileobie.echo.external.ReconnectBackoff
import com.mobileobie.echo.external.externalSessionMetadata
import com.mobileobie.echo.external.transport.TcpExternalPcmSource
import com.mobileobie.echo.vad.HeuristicVoiceActivityDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ActiveListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private var timing = ActiveListeningTimingConfig()
    private var detector = ConversationDetector(timing)
    private var vad = HeuristicVoiceActivityDetector()
    private var preRoll = RollingAudioBuffer.forDuration(
        StreamingAudioCapture.SAMPLE_RATE_HZ,
        timing.preRollBufferMs,
    )
    private var audioCapture: StreamingPcmSource = StreamingAudioCapture()
    private var activeSource = ActiveListeningSource.PHONE
    private var sourceName: String? = null
    private var reconnectCount = 0
    private val reconnectBackoff = ReconnectBackoff()
    private lateinit var notification: ActiveListeningNotification
    private var captureJob: Job? = null
    private var writer: PcmConversationWriter? = null
    private var captureStartedAtUtcMillis: Long? = null
    private var vadFramesSinceLog = 0
    private var vadSpeechFramesSinceLog = 0
    @Volatile private var configurationRefreshPending = false
    @Volatile private var configurationRefreshNotBeforeElapsedRealtime = 0L

    private val container get() = (application as HuhApplication).container

    override fun onCreate() {
        super.onCreate()
        notification = ActiveListeningNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_START) {
            selectPhoneSource()
            promote(ActiveListeningState.STARTING)
        }
        if (action == ACTION_START_EXTERNAL) {
            selectExternalSource(requireNotNull(intent))
            promote(ActiveListeningState.STARTING)
        }
        if (action == ACTION_PAUSE) setState(ActiveListeningState.PAUSED)
        if (action == ACTION_RESUME) setState(ActiveListeningState.STARTING)
        scope.launch {
            commandMutex.withLock {
                when (action) {
                    ACTION_START, ACTION_START_EXTERNAL, ACTION_RESUME -> startCapture()
                    ACTION_PAUSE -> pauseCapture()
                    ACTION_TURN_OFF -> turnOff()
                    ACTION_REFRESH_CONFIGURATION -> {
                        configurationRefreshPending = true
                        configurationRefreshNotBeforeElapsedRealtime =
                            SystemClock.elapsedRealtime() + CONFIGURATION_REFRESH_DEBOUNCE_MS
                        Log.i(TAG, "Capture configuration refresh requested")
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun promote(state: ActiveListeningState) {
        ServiceCompat.startForeground(
            this,
            ActiveListeningNotification.NOTIFICATION_ID,
            notification.build(
                ActiveListeningSnapshot(state = state, source = activeSource, sourceName = sourceName)
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (activeSource == ActiveListeningSource.PHONE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
            } else {
                0
            },
        )
        setState(state)
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return
        if (activeSource == ActiveListeningSource.PHONE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showError("Microphone permission is required")
            return
        }
        reloadCaptureConfiguration()
        setState(if (activeSource == ActiveListeningSource.PHONE) ActiveListeningState.WAITING else ActiveListeningState.STARTING)
        captureJob = scope.launch {
            if (activeSource == ActiveListeningSource.EXTERNAL_DEVICE) captureExternalWithReconnect()
            else capturePhoneMicrophone()
        }
    }

    private suspend fun capturePhoneMicrophone() {
        try {
            audioCapture.capture(::onSourceEvent)
        } catch (_: CancellationException) {
            // Expected when pausing or turning off.
        } catch (error: AudioRouteChangedException) {
            Log.i(TAG, "Audio route changed; reinitializing capture")
            discardWriter()
            detector.reset()
            showError("Audio device changed. Reconnecting…")
        } catch (error: Throwable) {
            Log.e(TAG, "Active capture failed", error)
            discardWriter()
            detector.reset()
            showError(error.message ?: "Microphone unavailable")
        }
    }

    private suspend fun captureExternalWithReconnect() {
        var attempt = 0
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                audioCapture.capture(::onSourceEvent)
                attempt++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "External audio connection failed", error)
                finalizeExternalInterruption()
                attempt++
            }
            reconnectCount++
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                setError("Could not reconnect to ${sourceName ?: "the external device"}")
                return
            }
            setState(ActiveListeningState.RECONNECTING, reconnectAttempt = attempt)
            delay(reconnectBackoff.delayMs(attempt))
        }
    }

    private fun onSourceEvent(event: PcmSourceEvent) {
        when (event) {
            is PcmSourceEvent.Ready -> {
                sourceName = event.descriptor.deviceName ?: sourceName
                setState(ActiveListeningState.WAITING)
            }
            is PcmSourceEvent.StreamStarted -> Unit
            is PcmSourceEvent.Audio -> onAudioFrame(event.samples)
            is PcmSourceEvent.StreamStopped -> finishExternalStream(event.reason)
            is PcmSourceEvent.Disconnected -> if (activeSource == ActiveListeningSource.EXTERNAL_DEVICE) {
                setState(ActiveListeningState.RECONNECTING)
            }
        }
    }

    private fun finishExternalStream(reason: PcmSourceEndReason) {
        val update = detector.turnOff()
        finishBoundary(update)
        if (reason == PcmSourceEndReason.DISCONNECT || reason == PcmSourceEndReason.DEVICE_REBOOT ||
            reason == PcmSourceEndReason.CAPTURE_FAILURE
        ) setState(ActiveListeningState.RECONNECTING) else setState(ActiveListeningState.WAITING)
    }

    private fun finalizeExternalInterruption() {
        if (writer == null) return
        val update = detector.turnOff()
        finishBoundary(update)
    }

    private fun finishBoundary(update: ConversationUpdate) {
        val completed = if (update.directive == ConversationDirective.FINALIZE_CAPTURE) writer?.finish() else null
        if (completed == null) discardWriter()
        writer = null
        captureStartedAtUtcMillis = null
        if (update.state == ConversationState.FINALIZING) detector.completeFinalization()
        detector.reset()
        preRoll.clear()
        if (completed != null) {
            val externalMetadata = currentExternalMetadata()
            scope.launch { persistAndQueue(completed, update, externalMetadata) }
        }
    }

    private fun onAudioFrame(frame: ShortArray) {
        if (writer == null && configurationRefreshPending &&
            SystemClock.elapsedRealtime() >= configurationRefreshNotBeforeElapsedRealtime
        ) reloadCaptureConfiguration()
        val hadWriter = writer != null
        if (!hadWriter) preRoll.append(frame)
        val activity = vad.process(frame)
        logVadDiagnostics(activity)
        val update = detector.process(activity, StreamingAudioCapture.FRAME_DURATION_MS)
        if (hadWriter) writer?.write(frame)

        if (update.stateChanged) {
            Log.d(
                TAG,
                "${update.previousState} -> ${update.state}; " +
                    "speech=${update.cumulativeSpeechDurationMs}ms " +
                    "segments=${update.speechSegmentCount} silence=${update.observedSilenceMs}ms " +
                    "longestInternalSilence=${update.longestInternalSilenceMs}ms",
            )
        }
        when (update.directive) {
            ConversationDirective.START_CAPTURE -> {
                runCatching {
                    writer = PcmConversationWriter.create(filesDir).also { it.write(preRoll.snapshot()) }
                    preRoll.clear()
                    captureStartedAtUtcMillis = System.currentTimeMillis()
                    setState(ActiveListeningState.LISTENING)
                }.onFailure { showError("There isn't enough storage to save this conversation") }
            }
            ConversationDirective.FINALIZE_CAPTURE -> finishConversation(queue = true, update = update)
            ConversationDirective.DISCARD_CAPTURE -> finishConversation(queue = false, update = update)
            ConversationDirective.NONE -> if (update.state == ConversationState.LISTENING &&
                ActiveListeningRuntime.snapshot.value.state != ActiveListeningState.LISTENING
            ) setState(ActiveListeningState.LISTENING)
        }
    }

    private fun finishConversation(queue: Boolean, update: ConversationUpdate) {
        val completed = if (queue) writer?.finish() else null
        if (!queue) discardWriter()
        writer = null
        captureStartedAtUtcMillis = null
        detector.completeFinalization()
        preRoll.clear()
        setState(ActiveListeningState.WAITING)
        if (completed != null) {
            val externalMetadata = currentExternalMetadata()
            scope.launch { persistAndQueue(completed, update, externalMetadata) }
        }
    }

    private suspend fun persistAndQueue(
        audio: CompletedPcm,
        timingUpdate: ConversationUpdate,
        externalMetadata: ExternalDeviceSessionMetadata?,
    ) {
        runCatching {
            val session = SessionMetadata.createTranscribing(
                durationMillis = audio.durationMillis,
                transcriptionModel = container.activeListeningSettings.transcriptionModel,
                source = audioCapture.descriptor.sessionSource,
                audioPath = audio.file.absolutePath,
                speechDurationMillis = timingUpdate.cumulativeSpeechDurationMs,
                speechSegmentCount = timingUpdate.speechSegmentCount,
                longestInternalSilenceMillis = timingUpdate.longestInternalSilenceMs,
                conversationEndSilenceMillis = timing.conversationEndSilenceMs,
                externalDevice = externalMetadata,
            )
            container.sessionRepository.create(session)
            container.transcriptionQueue.enqueue(session)
        }.onFailure { error ->
            Log.e(TAG, "Could not queue active conversation", error)
            showError("The conversation could not be saved")
        }
    }

    private suspend fun pauseCapture() {
        stopMicrophone()
        val update = detector.pause()
        val completed = when (update.directive) {
            ConversationDirective.FINALIZE_CAPTURE -> writer?.finish()
            else -> null
        }
        if (update.directive != ConversationDirective.FINALIZE_CAPTURE) discardWriter()
        writer = null
        captureStartedAtUtcMillis = null
        if (update.state == ConversationState.FINALIZING) detector.completeFinalization()
        if (completed != null) persistAndQueue(completed, update, currentExternalMetadata())
        setState(ActiveListeningState.PAUSED)
    }

    private suspend fun turnOff() {
        stopMicrophone()
        val update = detector.turnOff()
        val completed = when (update.directive) {
            ConversationDirective.FINALIZE_CAPTURE -> writer?.finish()
            else -> null
        }
        if (update.directive != ConversationDirective.FINALIZE_CAPTURE) discardWriter()
        writer = null
        captureStartedAtUtcMillis = null
        if (update.state == ConversationState.FINALIZING) detector.completeFinalization()
        if (completed != null) persistAndQueue(completed, update, currentExternalMetadata())
        ActiveListeningRuntime.update(ActiveListeningState.OFF)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopMicrophone() {
        captureJob?.cancel()
        audioCapture.stop()
        captureJob = null
    }

    private fun discardWriter() {
        writer?.discard()
        writer = null
        captureStartedAtUtcMillis = null
    }

    private fun setState(state: ActiveListeningState) {
        setState(state, reconnectAttempt = 0)
    }

    private fun setState(state: ActiveListeningState, reconnectAttempt: Int) {
        ActiveListeningRuntime.update(
            state = state,
            captureStartedAtUtcMillis = captureStartedAtUtcMillis,
            source = activeSource,
            sourceName = sourceName,
            reconnectAttempt = reconnectAttempt,
        )
        notification.notify(ActiveListeningRuntime.snapshot.value)
    }

    private fun showError(message: String) {
        stopMicrophone()
        captureStartedAtUtcMillis = null
        setError(message)
    }

    private fun setError(message: String) {
        ActiveListeningRuntime.update(
            ActiveListeningState.ERROR, message, source = activeSource, sourceName = sourceName
        )
        notification.notify(ActiveListeningRuntime.snapshot.value)
    }

    private fun selectPhoneSource() {
        if (captureJob?.isActive == true) return
        activeSource = ActiveListeningSource.PHONE
        sourceName = null
        reconnectCount = 0
        audioCapture = StreamingAudioCapture()
    }

    private fun selectExternalSource(intent: Intent) {
        if (captureJob?.isActive == true) return
        val endpoint = ExternalDeviceEndpoint(
            host = intent.getStringExtra(EXTRA_HOST).orEmpty(),
            port = intent.getIntExtra(EXTRA_PORT, ExternalDeviceEndpoint.DEFAULT_PORT),
            expectedDeviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty(),
            displayName = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty().ifBlank { "External device" },
        ).validated()
        activeSource = ActiveListeningSource.EXTERNAL_DEVICE
        sourceName = endpoint.displayName
        reconnectCount = 0
        audioCapture = TcpExternalPcmSource(
            endpoint.host,
            endpoint.port,
            endpoint.expectedDeviceId.ifBlank { null },
        )
    }

    private fun currentExternalMetadata(): ExternalDeviceSessionMetadata? =
        audioCapture.descriptor.externalSessionMetadata(audioCapture.diagnostics, reconnectCount)

    private fun reloadCaptureConfiguration() {
        timing = container.activeListeningSettings.timingConfig()
        detector = ConversationDetector(timing)
        vad = HeuristicVoiceActivityDetector()
        preRoll = RollingAudioBuffer.forDuration(
            StreamingAudioCapture.SAMPLE_RATE_HZ,
            timing.preRollBufferMs,
        )
        vadFramesSinceLog = 0
        vadSpeechFramesSinceLog = 0
        configurationRefreshPending = false
        Log.i(
            TAG,
            "Capture configured: start=${timing.speechStartThresholdMs}ms " +
                "minimumSpeech=${timing.minimumTranscriptSpeechMs}ms " +
                "endSilence=${timing.conversationEndSilenceMs}ms " +
                "preRoll=${timing.preRollBufferMs}ms",
        )
    }

    private fun logVadDiagnostics(activity: com.mobileobie.echo.vad.VoiceActivity) {
        vadFramesSinceLog++
        if (activity == com.mobileobie.echo.vad.VoiceActivity.SPEECH) vadSpeechFramesSinceLog++
        if (vadFramesSinceLog < VAD_LOG_INTERVAL_FRAMES) return

        val diagnostics = vad.diagnostics
        Log.d(
            TAG,
            "VAD window: speechFrames=$vadSpeechFramesSinceLog/$vadFramesSinceLog " +
                "rms=${diagnostics.rms.toInt()} peak=${diagnostics.peak} " +
                "noise=${diagnostics.estimatedNoiseRms.toInt()} " +
                "threshold=${diagnostics.energyThreshold.toInt()} " +
                "zcr=${"%.3f".format(diagnostics.zeroCrossingRate)} " +
                "crest=${"%.2f".format(diagnostics.crestFactor)} " +
                "modulation=${"%.3f".format(diagnostics.energyModulation)}",
        )
        vadFramesSinceLog = 0
        vadSpeechFramesSinceLog = 0
    }

    override fun onDestroy() {
        stopMicrophone()
        discardWriter()
        scope.cancel()
        if (ActiveListeningRuntime.snapshot.value.state != ActiveListeningState.OFF) {
            ActiveListeningRuntime.update(ActiveListeningState.OFF)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.mobileobie.echo.active.START"
        const val ACTION_START_EXTERNAL = "com.mobileobie.echo.active.START_EXTERNAL"
        const val ACTION_PAUSE = "com.mobileobie.echo.active.PAUSE"
        const val ACTION_RESUME = "com.mobileobie.echo.active.RESUME"
        const val ACTION_TURN_OFF = "com.mobileobie.echo.active.TURN_OFF"
        const val ACTION_REFRESH_CONFIGURATION = "com.mobileobie.echo.active.REFRESH_CONFIGURATION"
        private const val TAG = "ActiveListening"
        private const val VAD_LOG_INTERVAL_FRAMES = 250
        private const val CONFIGURATION_REFRESH_DEBOUNCE_MS = 300L
        const val EXTRA_HOST = "external_host"
        const val EXTRA_PORT = "external_port"
        const val EXTRA_DEVICE_ID = "external_device_id"
        const val EXTRA_DEVICE_NAME = "external_device_name"
        private const val MAX_RECONNECT_ATTEMPTS = 8
    }
}

private data class CompletedPcm(val file: File, val durationMillis: Long)

private class PcmConversationWriter private constructor(
    private val partialFile: File,
    private val output: BufferedOutputStream,
) {
    private var samplesWritten = 0L

    fun write(samples: ShortArray) {
        if (samples.isEmpty()) return
        val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(bytes::putShort)
        output.write(bytes.array())
        samplesWritten += samples.size
    }

    fun finish(): CompletedPcm {
        output.flush()
        output.close()
        val completed = File(partialFile.parentFile, partialFile.name.removeSuffix(".partial") + ".pcm")
        check(partialFile.renameTo(completed)) { "The conversation audio could not be finalized." }
        return CompletedPcm(
            completed,
            samplesWritten * 1_000L / StreamingAudioCapture.SAMPLE_RATE_HZ,
        )
    }

    fun discard() {
        runCatching { output.close() }
        partialFile.delete()
    }

    companion object {
        fun create(filesDir: File): PcmConversationWriter {
            val directory = File(filesDir, "active_audio").apply { mkdirs() }
            check(directory.isDirectory) { "Audio storage is unavailable." }
            val file = File.createTempFile("conversation_", ".partial", directory)
            return PcmConversationWriter(file, BufferedOutputStream(FileOutputStream(file)))
        }
    }
}
