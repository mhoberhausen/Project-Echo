package com.echokeep.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalDrawer
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echokeep.app.model.RecorderUiState
import com.echokeep.app.model.SessionMetadata
import com.echokeep.app.model.SessionRecord
import com.echokeep.app.model.SessionStatus
import com.echokeep.app.model.TranscriptionModel
import kotlinx.coroutines.launch

private enum class AppDestination { HOME, MANUAL, LIVE, SESSION, PREFERENCES }
private enum class ChatFilter(val label: String) { ALL("All sessions"), PENDING("Pending"), PROCESSED("Processed") }

@Composable
fun EchoKeepApp(
    recorderState: RecorderUiState,
    sessions: List<SessionRecord>,
    darkTheme: Boolean,
    silenceSeconds: Float,
    onDarkThemeChanged: (Boolean) -> Unit,
    onSilenceSecondsChanged: (Float) -> Unit,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onModelSelected: (TranscriptionModel) -> Unit,
    onProcess: () -> Unit,
    onCancelRecording: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onProcessSession: (String) -> Unit,
    onShareSession: (SessionRecord) -> Unit,
) {
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var chatFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navigate: (AppDestination) -> Unit = {
        destination = it
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = destination != AppDestination.HOME) {
        if (destination == AppDestination.MANUAL && recorderState.phase == com.echokeep.app.model.RecordingPhase.RECORDING) {
            onCancelRecording()
        }
        destination = AppDestination.HOME
    }
    LaunchedEffect(destination, selectedSession) {
        if (destination == AppDestination.SESSION && selectedSession == null) {
            destination = AppDestination.HOME
        }
    }

    ModalDrawer(
        drawerState = drawerState,
        drawerBackgroundColor = Color.Transparent,
        drawerElevation = 0.dp,
        drawerContent = {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Surface(modifier = Modifier.fillMaxSize(), elevation = 16.dp) {
                    NavigationDrawer(
                        filter = chatFilter,
                        sessions = sessions,
                        onFilterChanged = { chatFilter = it },
                        onHome = { navigate(AppDestination.HOME) },
                        onSession = { session ->
                            selectedSessionId = session.id
                            navigate(AppDestination.SESSION)
                        },
                        onPreferences = { navigate(AppDestination.PREFERENCES) },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text(destination.title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Open navigation",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (destination) {
                    AppDestination.HOME -> ModeHome(
                        onManual = { destination = AppDestination.MANUAL },
                        onLive = { destination = AppDestination.LIVE },
                    )
                    AppDestination.MANUAL -> RecorderScreen(
                        state = recorderState,
                        onRecord = onRecord,
                        onStop = onStop,
                        onClear = onClear,
                        onModelSelected = onModelSelected,
                        onProcess = onProcess,
                        showAppTitle = false,
                    )
                    AppDestination.LIVE -> LiveModePlaceholder()
                    AppDestination.SESSION -> selectedSession?.let { session ->
                        SessionDetailScreen(
                            session = session,
                            onProcess = { onProcessSession(session.id) },
                            onShare = { onShareSession(session) },
                            onDelete = {
                                destination = AppDestination.HOME
                                selectedSessionId = null
                                onDeleteSession(session.id)
                            },
                        )
                    }
                    AppDestination.PREFERENCES -> PreferencesScreen(
                        darkTheme = darkTheme,
                        selectedModel = recorderState.selectedModel,
                        silenceSeconds = silenceSeconds,
                        onDarkThemeChanged = onDarkThemeChanged,
                        onModelSelected = onModelSelected,
                        onSilenceSecondsChanged = onSilenceSecondsChanged,
                    )
                }
            }
        }
    }
}

private val AppDestination.title: String
    get() = when (this) {
        AppDestination.HOME -> "Echo Keep"
        AppDestination.MANUAL -> "Manual Mode"
        AppDestination.LIVE -> "Live Mode"
        AppDestination.SESSION -> "Saved Session"
        AppDestination.PREFERENCES -> "Preferences"
    }

@Composable
private fun ModeHome(onManual: () -> Unit, onLive: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(0.10f), contentAlignment = Alignment.BottomStart) {
            Text("Choose capture mode", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(0.05f))
        Card(
            modifier = Modifier.fillMaxWidth().weight(0.80f),
            shape = RoundedCornerShape(28.dp),
            elevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                val manualColor = MaterialTheme.colors.primary
                val liveColor = MaterialTheme.colors.secondary
                Canvas(Modifier.fillMaxSize()) {
                    val splitLeft = size.height * 0.62f
                    val splitRight = size.height * 0.38f
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, 0f)
                            lineTo(size.width, splitRight); lineTo(0f, splitLeft); close()
                        },
                        manualColor,
                    )
                    drawPath(
                        Path().apply {
                            moveTo(0f, splitLeft); lineTo(size.width, splitRight)
                            lineTo(size.width, size.height); lineTo(0f, size.height); close()
                        },
                        liveColor,
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.2f),
                        start = Offset(0f, splitLeft),
                        end = Offset(size.width, splitRight),
                        strokeWidth = 8f,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).clickable(onClick = onManual).padding(24.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text("MANUAL", color = MaterialTheme.colors.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Record, stop, then review", color = MaterialTheme.colors.onPrimary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                        .clickable(onClick = onLive).padding(24.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text("LIVE", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Conversation capture", textAlign = TextAlign.End)
                }
            }
        }
        Spacer(Modifier.weight(0.05f))
    }
}

@Composable
private fun NavigationDrawer(
    filter: ChatFilter,
    sessions: List<SessionRecord>,
    onFilterChanged: (ChatFilter) -> Unit,
    onHome: () -> Unit,
    onSession: (SessionRecord) -> Unit,
    onPreferences: () -> Unit,
) {
    var filterOpen by remember { mutableStateOf(false) }
    val filteredSessions = sessions.filter { session ->
        when (filter) {
            ChatFilter.ALL -> true
            ChatFilter.PENDING -> session.status.isPending
            ChatFilter.PROCESSED -> !session.status.isPending
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Echo Keep", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onHome, modifier = Modifier.padding(top = 10.dp)) { Text("Choose capture mode") }
        Divider(Modifier.padding(vertical = 12.dp))
        Text("Sessions", fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(onClick = { filterOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Filter: ${filter.label}")
            }
            DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                ChatFilter.entries.forEach { option ->
                    DropdownMenuItem(onClick = {
                        onFilterChanged(option)
                        filterOpen = false
                    }) { Text(option.label) }
                }
            }
        }
        if (filteredSessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (sessions.isEmpty()) "No saved sessions yet" else "No ${filter.label.lowercase()}", fontWeight = FontWeight.Bold)
                Text(
                    if (sessions.isEmpty()) "Completed transcriptions will appear here." else "Try a different filter.",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredSessions, key = { it.id }) { session ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onSession(session) }.padding(vertical = 10.dp),
                    ) {
                        Text(session.title, fontWeight = FontWeight.Bold)
                        Text(
                            "${SessionMetadata.displayDate(session.createdAtUtcMillis)} · ${SessionMetadata.displayDuration(session.durationMillis)}",
                            fontSize = 13.sp,
                        )
                        Text(session.status.displayName, fontSize = 13.sp, color = MaterialTheme.colors.primary)
                    }
                    Divider()
                }
            }
        }
        Divider()
        TextButton(onClick = onPreferences, modifier = Modifier.fillMaxWidth()) { Text("Preferences") }
    }
}

@Composable
private fun SessionDetailScreen(
    session: SessionRecord,
    onProcess: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text(session.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            SessionMetadata.displayDate(session.createdAtUtcMillis),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "${SessionMetadata.displayDuration(session.durationMillis)} · ${session.status.displayName} · ${session.transcriptionModel.displayName}",
            modifier = Modifier.padding(top = 4.dp),
        )
        SessionContentCard("Transcript", session.transcript)
        session.processText?.takeIf(String::isNotBlank)?.let { SessionContentCard("Processed", it) }
        if (session.tags.isNotEmpty()) {
            Text("Tags", modifier = Modifier.padding(top = 20.dp), fontWeight = FontWeight.Bold)
            Text(session.tags.joinToString(" · "), modifier = Modifier.padding(top = 6.dp))
        }
        if (session.status != SessionStatus.PROCESSED) {
            val processing = session.status in setOf(SessionStatus.QUEUED, SessionStatus.PROCESSING)
            Button(
                onClick = onProcess,
                enabled = !processing,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text(if (processing) "Processing…" else "Process")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (session.status == SessionStatus.PROCESSED) 24.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete session?") },
            text = { Text("This permanently removes the saved transcript and processed result from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionContentCard(title: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun LiveModePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Live Mode", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            "Soon, silence detection will split an ongoing conversation into local transcript messages.",
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
        Card(modifier = Modifier.padding(top = 24.dp)) {
            Text("UI preview only — no continuous capture is active.", modifier = Modifier.padding(18.dp))
        }
    }
}

@Composable
private fun PreferencesScreen(
    darkTheme: Boolean,
    selectedModel: TranscriptionModel,
    silenceSeconds: Float,
    onDarkThemeChanged: (Boolean) -> Unit,
    onModelSelected: (TranscriptionModel) -> Unit,
    onSilenceSecondsChanged: (Float) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        PreferenceHeading("Appearance")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dark mode", fontWeight = FontWeight.Bold)
                Text(if (darkTheme) "Dark appearance" else "Light appearance")
            }
            Switch(checked = darkTheme, onCheckedChange = onDarkThemeChanged)
        }
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Transcription model")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TranscriptionModel.entries.forEach { model ->
                if (model == selectedModel) {
                    Button(onClick = { onModelSelected(model) }) { Text(model.displayName) }
                } else {
                    OutlinedButton(onClick = { onModelSelected(model) }) { Text(model.displayName) }
                }
            }
        }
        Text("Used for the next Manual or Live transcription.", modifier = Modifier.padding(top = 8.dp))
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Silence capture")
        Text("Minimum silence: ${"%.1f".format(silenceSeconds)} seconds", fontWeight = FontWeight.Bold)
        Slider(
            value = silenceSeconds,
            onValueChange = onSilenceSecondsChanged,
            valueRange = 0.5f..4f,
            steps = 6,
        )
        Text("Preview setting — it will apply when Live Mode segmentation is implemented.")
        Spacer(Modifier.height(12.dp))
        Text("Preferences are currently kept for this app session only.")
    }
}

@Composable
private fun PreferenceHeading(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
}
