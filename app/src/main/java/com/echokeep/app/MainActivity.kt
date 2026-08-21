package com.echokeep.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echokeep.app.audio.AndroidAudioRecorder
import com.echokeep.app.interpretation.GemmaTranscriptInterpreter
import com.echokeep.app.transcription.TranscriptCleaner
import com.echokeep.app.transcription.WhisperTranscriber
import com.echokeep.app.ui.EchoKeepTheme
import com.echokeep.app.ui.RecorderScreen
import com.echokeep.app.ui.RecorderViewModel
import com.echokeep.app.ui.RecorderViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = RecorderViewModelFactory(
            recorder = AndroidAudioRecorder(),
            transcriber = WhisperTranscriber(applicationContext),
            cleaner = TranscriptCleaner(),
            interpreter = GemmaTranscriptInterpreter(applicationContext),
        )

        setContent {
            val recorderViewModel: RecorderViewModel = viewModel(factory = factory)
            val state by recorderViewModel.uiState.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) recorderViewModel.startRecording()
                else recorderViewModel.permissionDenied()
            }

            EchoKeepTheme {
                RecorderScreen(
                    state = state,
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
