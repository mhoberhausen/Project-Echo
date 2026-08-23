package com.huh.app

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
import com.huh.app.audio.AndroidAudioRecorder
import com.huh.app.active.ActiveListeningController
import com.huh.app.active.ActiveListeningRuntime
import com.huh.app.active.ActiveListeningState
import com.huh.app.interpretation.GemmaTranscriptInterpreter
import com.huh.app.model.shareText
import com.huh.app.ui.HuhTheme
import com.huh.app.ui.HuhApp
import com.huh.app.ui.RecorderViewModel
import com.huh.app.ui.RecorderViewModelFactory

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
                )
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ACTIVE_LISTENING = "open_active_listening"
    }
}
