package com.mobileobie.echo

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileobie.echo.audio.AndroidAudioRecorder
import com.mobileobie.echo.active.ActiveListeningController
import com.mobileobie.echo.active.ActiveListeningRuntime
import com.mobileobie.echo.active.ActiveListeningState
import com.mobileobie.echo.active.ActiveListeningSource
import com.mobileobie.echo.external.ExternalDeviceEndpoint
import com.mobileobie.echo.interpretation.GemmaTranscriptInterpreter
import com.mobileobie.echo.model.shareText
import com.mobileobie.echo.ui.HuhTheme
import com.mobileobie.echo.ui.HuhApp
import com.mobileobie.echo.ui.RecorderViewModel
import com.mobileobie.echo.ui.RecorderViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HuhApplication).container
        val factory = RecorderViewModelFactory(
            recorder = AndroidAudioRecorder(),
            transcriber = container.transcriber,
            cleaner = container.cleaner,
            interpreter = GemmaTranscriptInterpreter(applicationContext),
            sessionRepository = container.sessionRepository,
        )

        setContent {
            val recorderViewModel: RecorderViewModel = viewModel(factory = factory)
            val state by recorderViewModel.uiState.collectAsState()
            val sessions by recorderViewModel.sessions.collectAsState()
            val activeListening by ActiveListeningRuntime.snapshot.collectAsState()
            val queuedTranscriptions by container.transcriptionQueue.pendingCount.collectAsState()
            var darkTheme by remember { mutableStateOf(false) }
            var silenceSeconds by remember {
                mutableFloatStateOf(container.activeListeningSettings.conversationEndSilenceSeconds.toFloat())
            }
            var speechStartThresholdMs by remember {
                mutableFloatStateOf(container.activeListeningSettings.speechStartThresholdMs.toFloat())
            }
            var minimumTranscriptSpeechSeconds by remember {
                mutableFloatStateOf(container.activeListeningSettings.minimumTranscriptSpeechMs / 1_000f)
            }
            var preRollSeconds by remember {
                mutableFloatStateOf(container.activeListeningSettings.preRollBufferMs / 1_000f)
            }
            var minimumTranscriptWords by remember {
                mutableFloatStateOf(container.activeListeningSettings.minimumTranscriptWords.toFloat())
            }
            var externalEndpoint by remember {
                mutableStateOf(container.externalDeviceSettings.endpoint)
            }
            var pendingExternalEndpoint by remember { mutableStateOf<ExternalDeviceEndpoint?>(null) }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) recorderViewModel.startRecording()
                else recorderViewModel.permissionDenied()
            }
            val activePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val microphoneGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val notificationGranted = Build.VERSION.SDK_INT < 33 ||
                    grants[Manifest.permission.POST_NOTIFICATIONS] == true ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (microphoneGranted && notificationGranted) ActiveListeningController.turnOn(this)
                else {
                    val message = if (notificationGranted) {
                        "Microphone permission is required for active listening."
                    } else {
                        "Microphone and notification permissions are required so active listening is always visible."
                    }
                    ActiveListeningRuntime.update(ActiveListeningState.ERROR, message)
                }
            }
            val externalPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val localNetworkGranted = Build.VERSION.SDK_INT < 37 ||
                    grants[Manifest.permission.ACCESS_LOCAL_NETWORK] == true ||
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_LOCAL_NETWORK
                    ) == PackageManager.PERMISSION_GRANTED
                val notificationGranted = Build.VERSION.SDK_INT < 33 ||
                    grants[Manifest.permission.POST_NOTIFICATIONS] == true ||
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                val endpoint = pendingExternalEndpoint
                pendingExternalEndpoint = null
                if (localNetworkGranted && notificationGranted && endpoint != null) {
                    ActiveListeningController.turnOnExternal(this, endpoint)
                } else {
                    ActiveListeningRuntime.update(
                        ActiveListeningState.ERROR,
                        if (!localNetworkGranted) {
                            "Local network permission is required to connect to the external device."
                        } else "Notification permission is required so the connection remains visible.",
                        source = ActiveListeningSource.EXTERNAL_DEVICE,
                        sourceName = endpoint?.displayName,
                    )
                }
            }
            val refreshActiveListeningConfiguration = {
                if (activeListening.state != ActiveListeningState.OFF) {
                    ActiveListeningController.refreshConfiguration(this)
                }
            }

            HuhTheme(darkTheme = darkTheme) {
                HuhApp(
                    recorderState = state,
                    sessions = sessions,
                    darkTheme = darkTheme,
                    silenceSeconds = silenceSeconds,
                    onDarkThemeChanged = { darkTheme = it },
                    onSilenceSecondsChanged = {
                        silenceSeconds = it
                        container.activeListeningSettings.conversationEndSilenceSeconds = it.toInt()
                        refreshActiveListeningConfiguration()
                    },
                    onRecord = {
                        if (activeListening.state != ActiveListeningState.OFF) {
                            recorderViewModel.activeListeningBlocksManualRecording()
                        } else if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) recorderViewModel.startRecording()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = recorderViewModel::stopRecording,
                    onClear = recorderViewModel::clear,
                    onModelSelected = {
                        recorderViewModel.selectModel(it)
                        container.activeListeningSettings.transcriptionModel = it
                    },
                    onProcess = recorderViewModel::processTranscript,
                    onCancelRecording = recorderViewModel::cancelRecording,
                    onDeleteSession = recorderViewModel::deleteSession,
                    onProcessSession = recorderViewModel::processSavedSession,
                    onShareSession = { session ->
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, session.title)
                            putExtra(Intent.EXTRA_TEXT, session.shareText())
                        }
                        startActivity(Intent.createChooser(sendIntent, "Share session"))
                    },
                    activeListening = activeListening,
                    queuedTranscriptions = queuedTranscriptions,
                    openActiveListening = intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_LISTENING, false),
                    onEnableActiveListening = {
                        val permissions = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        activePermissionLauncher.launch(permissions.toTypedArray())
                    },
                    onPauseActiveListening = { ActiveListeningController.pause(this) },
                    onResumeActiveListening = {
                        if (activeListening.source == ActiveListeningSource.EXTERNAL_DEVICE) {
                            ActiveListeningController.resume(this)
                            return@HuhApp
                        }
                        val microphoneGranted = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                        if (microphoneGranted && notificationGranted) {
                            ActiveListeningController.turnOn(this)
                        } else {
                            val permissions = buildList {
                                add(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            activePermissionLauncher.launch(permissions.toTypedArray())
                        }
                    },
                    onTurnOffActiveListening = { ActiveListeningController.turnOff(this) },
                    onRetryTranscription = { container.transcriptionQueue.enqueue(it) },
                    speechStartThresholdMs = speechStartThresholdMs,
                    minimumTranscriptSpeechSeconds = minimumTranscriptSpeechSeconds,
                    preRollSeconds = preRollSeconds,
                    minimumTranscriptWords = minimumTranscriptWords,
                    onSpeechStartThresholdChanged = {
                        speechStartThresholdMs = it
                        container.activeListeningSettings.speechStartThresholdMs = it.toInt()
                        refreshActiveListeningConfiguration()
                    },
                    onMinimumTranscriptSpeechChanged = {
                        minimumTranscriptSpeechSeconds = it
                        container.activeListeningSettings.minimumTranscriptSpeechMs = (it * 1_000).toInt()
                        refreshActiveListeningConfiguration()
                    },
                    onPreRollChanged = {
                        preRollSeconds = it
                        container.activeListeningSettings.preRollBufferMs = (it * 1_000).toInt()
                        refreshActiveListeningConfiguration()
                    },
                    onMinimumTranscriptWordsChanged = {
                        minimumTranscriptWords = it
                        container.activeListeningSettings.minimumTranscriptWords = it.toInt()
                    },
                    onResetAdvancedDefaults = {
                        container.activeListeningSettings.resetAdvancedDefaults()
                        speechStartThresholdMs = container.activeListeningSettings.speechStartThresholdMs.toFloat()
                        minimumTranscriptSpeechSeconds =
                            container.activeListeningSettings.minimumTranscriptSpeechMs / 1_000f
                        silenceSeconds =
                            container.activeListeningSettings.conversationEndSilenceSeconds.toFloat()
                        preRollSeconds = container.activeListeningSettings.preRollBufferMs / 1_000f
                        minimumTranscriptWords =
                            container.activeListeningSettings.minimumTranscriptWords.toFloat()
                        refreshActiveListeningConfiguration()
                    },
                    externalDeviceEndpoint = externalEndpoint,
                    onSaveExternalDevice = {
                        container.externalDeviceSettings.endpoint = it
                        externalEndpoint = it
                    },
                    onConnectExternalDevice = { endpoint ->
                        pendingExternalEndpoint = endpoint
                        val permissions = buildList {
                            if (Build.VERSION.SDK_INT >= 37) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (permissions.isEmpty()) {
                            pendingExternalEndpoint = null
                            ActiveListeningController.turnOnExternal(this, endpoint)
                        } else externalPermissionLauncher.launch(permissions.toTypedArray())
                    },
                    onForgetExternalDevice = {
                        container.externalDeviceSettings.forget()
                        externalEndpoint = container.externalDeviceSettings.endpoint
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ACTIVE_LISTENING = "open_active_listening"
    }
}
