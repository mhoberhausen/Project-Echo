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
import com.mobileobie.echo.audio.SilenceChunkAccumulator
import com.mobileobie.echo.model.SessionMetadata
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.SessionSource
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.model.ExternalDeviceSessionMetadata
import com.mobileobie.echo.external.ExternalDeviceEndpoint
import com.mobileobie.echo.external.ReconnectBackoff
import com.mobileobie.echo.external.externalSessionMetadata
import com.mobileobie.echo.external.transport.TcpExternalPcmSource
import com.mobileobie.echo.vad.HeuristicVoiceActivityDetector
import com.mobileobie.echo.vad.VoiceActivity
import com.mobileobie.echo.transcription.IncrementalTranscriptionWorker
import com.mobileobie.echo.transcription.TimestampedTranscript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeoutOrNull
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
    @Volatile private var captureStopRequested = false
    private var writer: PcmConversationWriter? = null
    private var conversationChunker: SilenceChunkAccumulator? = null
    private var conversationTranscription: IncrementalTranscriptionWorker? = null
    private var conversationTranscriptionModel: TranscriptionModel? = null
    private var durableCapture: DurableCapture? = null
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
        captureStopRequested = false
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
        while (kotlinx.coroutines.currentCoroutineContext().isActive && !captureStopRequested) {
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
            if (captureStopRequested) return
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
            is PcmSourceEvent.StreamStarted -> if (activeSource == ActiveListeningSource.EXTERNAL_DEVICE) {
                captureStartedAtUtcMillis = System.currentTimeMillis()
                setState(ActiveListeningState.LISTENING)
            }
            is PcmSourceEvent.Audio -> onAudioFrame(event.samples)
            is PcmSourceEvent.FinalizedAudio -> acceptFinalizedExternalAudio(event)
            is PcmSourceEvent.StreamStopped -> finishExternalStream(event.reason)
            is PcmSourceEvent.Disconnected -> if (activeSource == ActiveListeningSource.EXTERNAL_DEVICE) {
                setState(ActiveListeningState.RECONNECTING)
            }
        }
    }

    private fun acceptFinalizedExternalAudio(event: PcmSourceEvent.FinalizedAudio) {
        check(activeSource == ActiveListeningSource.EXTERNAL_DEVICE) {
            "Only external sources can provide finalized audio files."
        }
        val completed = copyExternalAudio(event)
        val externalMetadata = currentExternalMetadata()
        scope.launch {
            persistAndQueue(
                audio = completed,
                speechDurationMillis = 0,
                speechSegmentCount = 0,
                longestInternalSilenceMillis = 0,
                conversationEndSilenceMillis = 0,
                externalMetadata = externalMetadata,
            )
        }
    }

    private fun copyExternalAudio(event: PcmSourceEvent.FinalizedAudio): CompletedPcm {
        check(event.file.isFile && event.file.length() > 0 && event.file.length() % 2L == 0L) {
            "The finalized external recording is invalid."
        }
        val directory = File(filesDir, "active_audio").apply { mkdirs() }
        check(directory.isDirectory) { "Audio storage is unavailable." }
        val destination = File.createTempFile("external_conversation_", ".pcm", directory)
        return try {
            event.file.copyTo(destination, overwrite = true)
            check(destination.length() == event.file.length()) {
                "The external recording could not be copied completely."
            }
            CompletedPcm(destination, event.durationMillis)
        } catch (error: Throwable) {
            destination.delete()
            throw error
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
            val incremental = takeIncrementalTranscription()
            container.scope.launch {
                persistAndQueue(
                    completed,
                    update,
                    externalMetadata,
                    finishIncrementalTranscription(incremental),
                    incremental?.model,
                    takeDurableCapture(),
                )
            }
        } else {
            cancelIncrementalTranscription()
            cancelDurableCapture()
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
        if (hadWriter) {
            writer?.write(frame)
            val chunkActivity = if (vad.diagnostics.rawSpeech) VoiceActivity.SPEECH else VoiceActivity.SILENCE
            conversationChunker?.append(frame, chunkActivity)?.let { conversationTranscription?.offer(it) }
        }

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
                    val initialAudio = preRoll.snapshot()
                    writer = PcmConversationWriter.create(filesDir).also { it.write(initialAudio) }
                    startDurableCapture(requireNotNull(writer))
                    startIncrementalTranscription(initialAudio)
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
            val incremental = takeIncrementalTranscription()
            container.scope.launch {
                persistAndQueue(
                    completed,
                    update,
                    externalMetadata,
                    finishIncrementalTranscription(incremental),
                    incremental?.model,
                    takeDurableCapture(),
                )
            }
        } else {
            cancelIncrementalTranscription()
            cancelDurableCapture()
        }
    }

    private suspend fun persistAndQueue(
        audio: CompletedPcm,
        timingUpdate: ConversationUpdate,
        externalMetadata: ExternalDeviceSessionMetadata?,
        precomputedTranscript: TimestampedTranscript? = null,
        transcriptionModel: TranscriptionModel? = null,
        durableCapture: DurableCapture? = null,
    ) = persistAndQueue(
        audio = audio,
        speechDurationMillis = timingUpdate.cumulativeSpeechDurationMs,
        speechSegmentCount = timingUpdate.speechSegmentCount,
        longestInternalSilenceMillis = timingUpdate.longestInternalSilenceMs,
        conversationEndSilenceMillis = timing.conversationEndSilenceMs,
        externalMetadata = externalMetadata,
        precomputedTranscript = precomputedTranscript,
        transcriptionModel = transcriptionModel,
        durableCapture = durableCapture,
    )

    private suspend fun persistAndQueue(
        audio: CompletedPcm,
        speechDurationMillis: Long,
        speechSegmentCount: Int,
        longestInternalSilenceMillis: Long,
        conversationEndSilenceMillis: Long,
        externalMetadata: ExternalDeviceSessionMetadata?,
        precomputedTranscript: TimestampedTranscript? = null,
        transcriptionModel: TranscriptionModel? = null,
        durableCapture: DurableCapture? = null,
    ) {
        var sessionWasPersisted = false
        runCatching {
            val durableSession = durableCapture?.let { durable ->
                durable.created.join()
                val finalized = durable.session.copy(
                    status = SessionStatus.TRANSCRIBING,
                    durationMillis = audio.durationMillis,
                    speechDurationMillis = speechDurationMillis,
                    speechSegmentCount = speechSegmentCount,
                    longestInternalSilenceMillis = longestInternalSilenceMillis,
                    conversationEndSilenceMillis = conversationEndSilenceMillis,
                    externalDevice = externalMetadata,
                    transcriptSegments = precomputedTranscript?.segments.orEmpty(),
                )
                container.sessionRepository.updateCapturedSession(finalized)
                sessionWasPersisted = true
                finalized
            }
            val session = durableSession ?: SessionMetadata.createTranscribing(
                durationMillis = audio.durationMillis,
                transcriptionModel = transcriptionModel
                    ?: container.activeListeningSettings.transcriptionModel,
                source = audioCapture.descriptor.sessionSource,
                audioPath = audio.file.absolutePath,
                speechDurationMillis = speechDurationMillis,
                speechSegmentCount = speechSegmentCount,
                longestInternalSilenceMillis = longestInternalSilenceMillis,
                conversationEndSilenceMillis = conversationEndSilenceMillis,
                externalDevice = externalMetadata,
                transcriptSegments = precomputedTranscript?.segments.orEmpty(),
            )
            if (durableSession == null) {
                container.sessionRepository.create(session)
                sessionWasPersisted = true
            }
            container.transcriptionQueue.enqueue(session)
        }.onFailure { error ->
            // A persisted session can recover and requeue after a process restart. Keep its
            // PCM instead of turning a transient queue failure into permanent data loss.
            if (!sessionWasPersisted) audio.file.delete()
            Log.e(TAG, "Could not queue active conversation", error)
            showError("The conversation could not be saved")
        }
    }

    private suspend fun pauseCapture() {
        stopCaptureGracefully(PcmSourceEndReason.PAUSE)
        val update = detector.pause()
        val completed = when (update.directive) {
            ConversationDirective.FINALIZE_CAPTURE -> writer?.finish()
            else -> null
        }
        if (update.directive != ConversationDirective.FINALIZE_CAPTURE) discardWriter()
        writer = null
        captureStartedAtUtcMillis = null
        if (update.state == ConversationState.FINALIZING) detector.completeFinalization()
        if (completed != null) {
            val incremental = takeIncrementalTranscription()
            persistAndQueue(
                completed,
                update,
                currentExternalMetadata(),
                finishIncrementalTranscription(incremental),
                incremental?.model,
                takeDurableCapture(),
            )
        } else {
            cancelIncrementalTranscription()
            cancelDurableCapture()
        }
        setState(ActiveListeningState.PAUSED)
    }

    private suspend fun turnOff() {
        stopCaptureGracefully(PcmSourceEndReason.USER_STOP)
        val update = detector.turnOff()
        val completed = when (update.directive) {
            ConversationDirective.FINALIZE_CAPTURE -> writer?.finish()
            else -> null
        }
        if (update.directive != ConversationDirective.FINALIZE_CAPTURE) discardWriter()
        writer = null
        captureStartedAtUtcMillis = null
        if (update.state == ConversationState.FINALIZING) detector.completeFinalization()
        if (completed != null) {
            val incremental = takeIncrementalTranscription()
            persistAndQueue(
                completed,
                update,
                currentExternalMetadata(),
                finishIncrementalTranscription(incremental),
                incremental?.model,
                takeDurableCapture(),
            )
        } else {
            cancelIncrementalTranscription()
            cancelDurableCapture()
        }
        ActiveListeningRuntime.update(ActiveListeningState.OFF)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun stopCaptureGracefully(reason: PcmSourceEndReason) {
        val job = captureJob
        captureStopRequested = true
        if (activeSource == ActiveListeningSource.EXTERNAL_DEVICE &&
            audioCapture is TcpExternalPcmSource
        ) {
            (audioCapture as TcpExternalPcmSource).requestStop(reason)
            if (withTimeoutOrNull(EXTERNAL_FINALIZE_TIMEOUT_MS) { job?.join() } == null) {
                job?.cancelAndJoin()
            }
        } else {
            job?.cancelAndJoin()
            audioCapture.stop()
        }
        captureJob = null
    }

    private fun abortCapture() {
        captureStopRequested = true
        audioCapture.stop()
        captureJob?.cancel()
        captureJob = null
    }

    private fun discardWriter() {
        writer?.discard()
        writer = null
        captureStartedAtUtcMillis = null
        cancelIncrementalTranscription()
        cancelDurableCapture()
    }

    private fun startDurableCapture(writer: PcmConversationWriter) {
        cancelDurableCapture()
        val session = SessionMetadata.createTranscribing(
            durationMillis = 0,
            transcriptionModel = container.activeListeningSettings.transcriptionModel,
            source = audioCapture.descriptor.sessionSource,
            audioPath = writer.file.absolutePath,
            externalDevice = currentExternalMetadata(),
            status = SessionStatus.CAPTURING,
        )
        durableCapture = DurableCapture(session, container.scope.launch {
            container.sessionRepository.create(session)
        })
    }

    private fun takeDurableCapture(): DurableCapture? = durableCapture.also { durableCapture = null }

    private fun cancelDurableCapture() {
        val capture = takeDurableCapture() ?: return
        container.scope.launch {
            capture.created.join()
            container.sessionRepository.delete(capture.session.id)
        }
    }

    private fun startIncrementalTranscription(initialAudio: ShortArray) {
        cancelIncrementalTranscription()
        val model = container.activeListeningSettings.transcriptionModel
        val chunker = SilenceChunkAccumulator(
            sampleRateHz = StreamingAudioCapture.SAMPLE_RATE_HZ,
            quietBoundaryMs = timing.quietBoundaryMs,
        )
        val worker = IncrementalTranscriptionWorker(
            container.scope,
            container.transcriber,
            model,
            onDiagnostic = { Log.i(TAG, it) },
        )
        conversationChunker = chunker
        conversationTranscription = worker
        conversationTranscriptionModel = model
        chunker.append(initialAudio, VoiceActivity.SPEECH)?.let(worker::offer)
    }

    private fun takeIncrementalTranscription(): ActiveIncrementalTranscription? {
        val chunker = conversationChunker
        val worker = conversationTranscription
        val model = conversationTranscriptionModel
        conversationChunker = null
        conversationTranscription = null
        conversationTranscriptionModel = null
        if (chunker == null || worker == null || model == null) {
            worker?.cancel()
            return null
        }
        return ActiveIncrementalTranscription(chunker, worker, model)
    }

    private suspend fun finishIncrementalTranscription(
        incremental: ActiveIncrementalTranscription?,
    ): TimestampedTranscript? {
        if (incremental == null) return null
        incremental.chunker.finish()?.let(incremental.worker::offer)
        return incremental.worker.finish()
    }

    private fun cancelIncrementalTranscription() {
        conversationTranscription?.cancel()
        conversationChunker = null
        conversationTranscription = null
        conversationTranscriptionModel = null
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
        abortCapture()
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
            File(filesDir, "external_transfer"),
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
                "quietBoundary=${timing.quietBoundaryMs}ms " +
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
        abortCapture()
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
        private const val EXTERNAL_FINALIZE_TIMEOUT_MS = 90_000L
        const val EXTRA_HOST = "external_host"
        const val EXTRA_PORT = "external_port"
        const val EXTRA_DEVICE_ID = "external_device_id"
        const val EXTRA_DEVICE_NAME = "external_device_name"
        private const val MAX_RECONNECT_ATTEMPTS = 8
    }
}

private data class ActiveIncrementalTranscription(
    val chunker: SilenceChunkAccumulator,
    val worker: IncrementalTranscriptionWorker,
    val model: TranscriptionModel,
)

private data class DurableCapture(val session: SessionRecord, val created: Job)

private data class CompletedPcm(val file: File, val durationMillis: Long)

private class PcmConversationWriter private constructor(
    private val partialFile: File,
    private val output: BufferedOutputStream,
) {
    val file: File get() = partialFile
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
        return CompletedPcm(
            partialFile,
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
            val file = File.createTempFile("conversation_", ".pcm", directory)
            return PcmConversationWriter(file, BufferedOutputStream(FileOutputStream(file)))
        }
    }
}
