package com.echokeep.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echokeep.app.model.RecorderUiState
import com.echokeep.app.model.RecordingPhase
import com.echokeep.app.model.TranscriptionModel

@Composable
fun RecorderScreen(
    state: RecorderUiState,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onModelSelected: (TranscriptionModel) -> Unit,
    onProcess: () -> Unit,
    showAppTitle: Boolean = true,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showAppTitle) {
                Text("Echo Keep", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Text(statusText(state), modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))

            when (state.phase) {
                RecordingPhase.IDLE -> {
                    Button(
                        onClick = onRecord,
                        modifier = Modifier.size(148.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Start listening",
                            modifier = Modifier.size(76.dp),
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    ModelSelector(state.selectedModel, onModelSelected)
                }
                RecordingPhase.RECORDING -> {
                    Text(formatElapsed(state.elapsedSeconds), fontSize = 42.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onStop) { Text("Stop listening") }
                }
                RecordingPhase.PROCESSING, RecordingPhase.INTERPRETING -> CircularProgressIndicator()
                RecordingPhase.COMPLETE -> TranscriptResult(state, onRecord, onProcess, onClear)
                RecordingPhase.PROCESSED -> ProcessedMessage(state, onRecord, onClear)
                RecordingPhase.ERROR -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(state.errorMessage.orEmpty(), modifier = Modifier.padding(18.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onRecord) { Text("Try again") }
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun ModelSelector(
    selected: TranscriptionModel,
    onSelected: (TranscriptionModel) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TranscriptionModel.entries.forEach { model ->
            if (model == selected) {
                Button(onClick = { onSelected(model) }) {
                    Text(model.displayName)
                }
            } else {
                OutlinedButton(onClick = { onSelected(model) }) {
                    Text(model.displayName)
                }
            }
        }
    }
}

@Composable
private fun TranscriptResult(
    state: RecorderUiState,
    onRecord: () -> Unit,
    onProcess: () -> Unit,
    onClear: () -> Unit,
) {
    var showOriginal by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Concise transcript", fontWeight = FontWeight.Bold)
            Text(state.cleanedTranscript, modifier = Modifier.padding(top = 10.dp))
        }
    }
    state.errorMessage?.let { message ->
        Text(message, modifier = Modifier.padding(top = 12.dp))
    }
    TextButton(onClick = { showOriginal = !showOriginal }) {
        Text(if (showOriginal) "Hide original" else "Show original")
    }
    if (showOriginal) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(state.originalTranscript, modifier = Modifier.padding(20.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onRecord) { Text("Record again") }
    Button(onClick = onProcess, modifier = Modifier.padding(top = 8.dp)) { Text("Process") }
    OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 8.dp)) {
        Text("Clear")
    }
}

@Composable
private fun ProcessedMessage(
    state: RecorderUiState,
    onRecord: () -> Unit,
    onClear: () -> Unit,
) {
    var showTranscript by remember { mutableStateOf(false) }
    val result = requireNotNull(state.processedMessage)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Processed message", fontWeight = FontWeight.Bold)
            Text(result.summary, modifier = Modifier.padding(top = 10.dp))
            Text("Intent · ${result.intent.displayName}", modifier = Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold)
            if (result.keyPoints.isNotEmpty()) {
                Text("Key points", modifier = Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold)
                result.keyPoints.forEach { Text("• $it", modifier = Modifier.padding(top = 4.dp)) }
            }
            if (result.actionItems.isNotEmpty()) {
                Text("Action items", modifier = Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold)
                result.actionItems.forEach { action ->
                    val due = action.dueDate?.let { " — $it" }.orEmpty()
                    Text("• ${action.text}$due", modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    TextButton(onClick = { showTranscript = !showTranscript }) {
        Text(if (showTranscript) "Hide transcript" else "Show transcript")
    }
    if (showTranscript) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Concise transcript", fontWeight = FontWeight.Bold)
                Text(state.cleanedTranscript, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onRecord) { Text("Record again") }
    OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 8.dp)) {
        Text("Clear")
    }
}

private fun statusText(state: RecorderUiState) = when (state.phase) {
    RecordingPhase.IDLE -> "Ready — processing stays on this device"
    RecordingPhase.RECORDING -> "Listening…"
    RecordingPhase.PROCESSING -> "Transcribing locally…"
    RecordingPhase.INTERPRETING -> "Processing locally with Gemma…"
    RecordingPhase.COMPLETE -> "Transcript ready"
    RecordingPhase.PROCESSED -> "Message processed"
    RecordingPhase.ERROR -> "Couldn’t finish"
}

private fun formatElapsed(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
