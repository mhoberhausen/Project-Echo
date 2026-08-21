package com.echokeep.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.echokeep.app.audio.AndroidAudioRecorder
import com.echokeep.app.interpretation.GemmaTranscriptInterpreter
import com.echokeep.app.transcription.TranscriptCleaner
import com.echokeep.app.transcription.WhisperTranscriber
import com.echokeep.app.ui.EchoKeepTheme
import com.echokeep.app.ui.EchoKeepApp
import com.echokeep.app.ui.RecorderViewModel
import com.echokeep.app.ui.RecorderViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = RecorderViewModelFactory(
            recorder = AndroidAudioRecorder(),
            transcriber = WhisperTranscriber(applicationContext),
            cleaner = TranscriptCleaner(),
            interpreter = GemmaTranscriptInterpreter(applicationContext),
        )

        setContent {
            val recorderViewModel: RecorderViewModel = viewModel(factory = factory)
            val state by recorderViewModel.uiState.collectAsState()
            var darkTheme by remember { mutableStateOf(false) }
            var silenceSeconds by remember { mutableFloatStateOf(1.5f) }
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

            EchoKeepTheme(darkTheme = darkTheme) {
                EchoKeepApp(
                    recorderState = state,
                    darkTheme = darkTheme,
                    silenceSeconds = silenceSeconds,
                    onDarkThemeChanged = { darkTheme = it },
                    onSilenceSecondsChanged = { silenceSeconds = it },
                    onRecord = {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) recorderViewModel.startRecording()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = recorderViewModel::stopRecording,
                    onClear = recorderViewModel::clear,
                    onModelSelected = recorderViewModel::selectModel,
                    onProcess = recorderViewModel::processTranscript,
                )
            }
        }
    }
}
