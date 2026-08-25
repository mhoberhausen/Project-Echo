package com.mobileobie.echo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileobie.echo.model.RecorderUiState
import com.mobileobie.echo.model.RecordingPhase
import com.mobileobie.echo.model.TranscriptionModel

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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showAppTitle) Text("Huh?", style = MaterialTheme.typography.h4)
            Text(statusText(state), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

            when (state.phase) {
                RecordingPhase.IDLE -> {
                    Button(
                        onClick = onRecord,
                        modifier = Modifier.size(148.dp).semantics { contentDescription = "Start listening" },
                        shape = CircleShape,
                    ) {
                        HuhMark(Modifier.size(92.dp), MaterialTheme.colors.onPrimary)
                    }
                    Spacer(Modifier.height(28.dp))
                    ModelSelector(state.selectedModel, onModelSelected)
                }
                RecordingPhase.RECORDING -> {
                    ListeningMark(true, Modifier.size(144.dp), HuhListening)
                    Spacer(Modifier.height(16.dp))
                    Text(formatElapsed(state.elapsedSeconds), fontSize = 42.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onStop) { Text("Stop listening") }
                }
                RecordingPhase.PROCESSING, RecordingPhase.INTERPRETING -> {
                    HuhMark(Modifier.size(96.dp), MaterialTheme.colors.primary)
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator()
                }
                RecordingPhase.COMPLETE -> TranscriptResult(state, onRecord, onProcess, onClear)
                RecordingPhase.PROCESSED -> ProcessedMessage(state, onRecord, onClear)
                RecordingPhase.ERROR -> {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Text(state.errorMessage.orEmpty(), modifier = Modifier.padding(18.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onRecord) { Text("Try again") }
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
            if (state.phase != RecordingPhase.ERROR) {
                Text(
                    "Processed on this device",
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ModelSelector(selected: TranscriptionModel, onSelected: (TranscriptionModel) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TranscriptionModel.entries.forEach { model ->
            if (model == selected) {
                Button(onClick = { onSelected(model) }) { Text(model.displayName) }
            } else {
                OutlinedButton(onClick = { onSelected(model) }) { Text(model.displayName) }
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("What Was Said", fontWeight = FontWeight.Bold)
            Text(state.cleanedTranscript, modifier = Modifier.padding(top = 10.dp))
        }
    }
    state.errorMessage?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
    TextButton(onClick = { showOriginal = !showOriginal }) {
        Text(if (showOriginal) "Hide raw transcript" else "Show raw transcript")
    }
    if (showOriginal) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text(state.originalTranscript, modifier = Modifier.padding(20.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onRecord) { Text("Listen again") }
    Button(onClick = onProcess, modifier = Modifier.padding(top = 8.dp)) { Text("Make sense of this") }
    OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 8.dp)) { Text("Clear") }
}

@Composable
private fun ProcessedMessage(state: RecorderUiState, onRecord: () -> Unit, onClear: () -> Unit) {
    var showTranscript by remember { mutableStateOf(false) }
    val result = requireNotNull(state.processedMessage)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("What I Got From It", fontWeight = FontWeight.Bold)
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
        Text(if (showTranscript) "Hide what was said" else "Show what was said")
    }
    if (showTranscript) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("What Was Said", fontWeight = FontWeight.Bold)
                Text(state.cleanedTranscript, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onRecord) { Text("Listen again") }
    OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 8.dp)) { Text("Clear") }
}

private fun statusText(state: RecorderUiState) = when (state.phase) {
    RecordingPhase.IDLE -> "Ready when you are."
    RecordingPhase.RECORDING -> "I’m listening…"
    RecordingPhase.PROCESSING -> "Turning speech into words…"
    RecordingPhase.INTERPRETING -> "Thinking about what I heard…"
    RecordingPhase.COMPLETE -> "Got it."
    RecordingPhase.PROCESSED -> "Done."
    RecordingPhase.ERROR -> "Couldn’t finish."
}

private fun formatElapsed(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
