package com.huh.app.active

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.huh.app.HuhApplication
import com.huh.app.audio.RollingAudioBuffer
import com.huh.app.audio.StreamingAudioCapture
import com.huh.app.audio.AudioRouteChangedException
import com.huh.app.model.SessionMetadata
import com.huh.app.model.SessionSource
import com.huh.app.model.TranscriptionModel
import com.huh.app.vad.HeuristicVoiceActivityDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    private val audioCapture = StreamingAudioCapture()
    private lateinit var notification: ActiveListeningNotification
    private var captureJob: Job? = null
    private var writer: PcmConversationWriter? = null
    private var captureStartedAtUtcMillis: Long? = null
    private var vadFramesSinceLog = 0
    private var vadSpeechFramesSinceLog = 0
    @Volatile private var configurationRefreshPending = false

    private val container get() = (application as HuhApplication).container

    override fun onCreate() {
        super.onCreate()
        notification = ActiveListeningNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_START) promote(ActiveListeningState.STARTING)
        if (action == ACTION_PAUSE) setState(ActiveListeningState.PAUSED)
        if (action == ACTION_RESUME) setState(ActiveListeningState.STARTING)
        scope.launch {
            commandMutex.withLock {
                when (action) {
                    ACTION_START, ACTION_RESUME -> startCapture()
                    ACTION_PAUSE -> pauseCapture()
                    ACTION_TURN_OFF -> turnOff()
                    ACTION_REFRESH_CONFIGURATION -> {
                        configurationRefreshPending = true
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
            notification.build(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
        setState(state)
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showError("Microphone permission is required")
            return
        }
        reloadCaptureConfiguration()
        setState(ActiveListeningState.WAITING)
        captureJob = scope.launch {
            try {
                audioCapture.capture(::onAudioFrame)
            } catch (_: CancellationException) {
                // Expected when pausing or turning off.
            } catch (error: AudioRouteChangedException) {
                Log.i(TAG, "Audio route changed; reinitializing capture")
                discardWriter()
                detector.reset()
                showError("Audio device changed. Reconnecting…")
                scope.launch {
                    delay(500)
                    commandMutex.withLock {
                        captureJob = null
                        if (ActiveListeningRuntime.snapshot.value.state == ActiveListeningState.ERROR) startCapture()
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Active capture failed", error)
                discardWriter()
                detector.reset()
                showError(error.message ?: "Microphone unavailable")
            }
        }
    }

    private fun onAudioFrame(frame: ShortArray) {
        if (writer == null && configurationRefreshPending) reloadCaptureConfiguration()
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
        if (completed != null) scope.launch { persistAndQueue(completed, update) }
    }

    private suspend fun persistAndQueue(audio: CompletedPcm, timingUpdate: ConversationUpdate) {
        runCatching {
            val session = SessionMetadata.createTranscribing(
                durationMillis = audio.durationMillis,
                transcriptionModel = container.activeListeningSettings.transcriptionModel,
                source = SessionSource.ACTIVE_LISTENING,
                audioPath = audio.file.absolutePath,
                speechDurationMillis = timingUpdate.cumulativeSpeechDurationMs,
                speechSegmentCount = timingUpdate.speechSegmentCount,
                longestInternalSilenceMillis = timingUpdate.longestInternalSilenceMs,
                conversationEndSilenceMillis = timing.conversationEndSilenceMs,
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
        if (completed != null) persistAndQueue(completed, update)
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
        if (completed != null) persistAndQueue(completed, update)
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
        ActiveListeningRuntime.update(state, captureStartedAtUtcMillis = captureStartedAtUtcMillis)
        notification.notify(state)
    }

    private fun showError(message: String) {
        stopMicrophone()
        captureStartedAtUtcMillis = null
        ActiveListeningRuntime.update(ActiveListeningState.ERROR, message)
        notification.notify(ActiveListeningState.ERROR, message)
    }

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

    private fun logVadDiagnostics(activity: com.huh.app.vad.VoiceActivity) {
        vadFramesSinceLog++
        if (activity == com.huh.app.vad.VoiceActivity.SPEECH) vadSpeechFramesSinceLog++
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
        const val ACTION_START = "com.huh.app.active.START"
        const val ACTION_PAUSE = "com.huh.app.active.PAUSE"
        const val ACTION_RESUME = "com.huh.app.active.RESUME"
        const val ACTION_TURN_OFF = "com.huh.app.active.TURN_OFF"
        const val ACTION_REFRESH_CONFIGURATION = "com.huh.app.active.REFRESH_CONFIGURATION"
        private const val TAG = "ActiveListening"
        private const val VAD_LOG_INTERVAL_FRAMES = 250
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
