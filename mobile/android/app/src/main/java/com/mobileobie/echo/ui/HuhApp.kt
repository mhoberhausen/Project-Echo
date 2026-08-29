package com.mobileobie.echo.ui

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileobie.echo.model.RecorderUiState
import com.mobileobie.echo.model.SessionMetadata
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.active.ActiveListeningSnapshot
import com.mobileobie.echo.active.ActiveListeningState
import com.mobileobie.echo.active.ActiveListeningSource
import com.mobileobie.echo.external.ExternalDeviceEndpoint
import com.mobileobie.echo.settings.AiProviderConfig
import com.mobileobie.echo.settings.AudioInputChoice
import kotlinx.coroutines.launch

private enum class AppDestination { HOME, MANUAL, LIVE, SESSION, PREFERENCES, ADVANCED, EXTERNAL_DEVICE, AI_SELECTION }
private enum class ChatFilter(val label: String) { ALL("All"), PENDING("Pending"), PROCESSED("Processed") }

@Composable
fun HuhApp(
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
    onEditTranscript: (String, String) -> Unit = { _, _ -> },
    onRenameSpeakers: (String, Map<String, String>) -> Unit = { _, _ -> },
    onShareSession: (SessionRecord) -> Unit,
    activeListening: ActiveListeningSnapshot = ActiveListeningSnapshot(),
    queuedTranscriptions: Int = 0,
    openActiveListening: Boolean = false,
    onEnableActiveListening: () -> Unit = {},
    onPauseActiveListening: () -> Unit = {},
    onResumeActiveListening: () -> Unit = {},
    onTurnOffActiveListening: () -> Unit = {},
    onRetryTranscription: (SessionRecord) -> Unit = {},
    speechStartThresholdMs: Float = 400f,
    minimumTranscriptSpeechSeconds: Float = 3f,
    preRollSeconds: Float = 2f,
    minimumTranscriptWords: Float = 3f,
    onSpeechStartThresholdChanged: (Float) -> Unit = {},
    onMinimumTranscriptSpeechChanged: (Float) -> Unit = {},
    onPreRollChanged: (Float) -> Unit = {},
    onMinimumTranscriptWordsChanged: (Float) -> Unit = {},
    onResetAdvancedDefaults: () -> Unit = {},
    externalDeviceEndpoint: ExternalDeviceEndpoint = ExternalDeviceEndpoint(),
    onSaveExternalDevice: (ExternalDeviceEndpoint) -> Unit = {},
    onConnectExternalDevice: (ExternalDeviceEndpoint) -> Unit = {},
    onForgetExternalDevice: () -> Unit = {},
    selectedAudioInput: AudioInputChoice = AudioInputChoice.PHONE,
    onAudioInputSelected: (AudioInputChoice) -> Unit = {},
    aiProviders: List<AiProviderConfig> = listOf(AiProviderConfig.ON_DEVICE_GEMMA),
    onAiProvidersChanged: (List<AiProviderConfig>) -> Unit = {},
) {
    var destination by remember {
        mutableStateOf(if (openActiveListening) AppDestination.LIVE else AppDestination.HOME)
    }
    var chatFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var showAudioInputs by remember { mutableStateOf(false) }
    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navigate: (AppDestination) -> Unit = {
        destination = it
        scope.launch { drawerState.close() }
    }
    val returnHome: () -> Unit = {
        if (destination == AppDestination.MANUAL && recorderState.phase == com.mobileobie.echo.model.RecordingPhase.RECORDING) {
            onCancelRecording()
        }
        destination = AppDestination.HOME
        selectedSessionId = null
        scope.launch { drawerState.close() }
    }
    val navigateBack: () -> Unit = {
        if (destination == AppDestination.ADVANCED || destination == AppDestination.EXTERNAL_DEVICE ||
            destination == AppDestination.AI_SELECTION
        ) {
            destination = AppDestination.PREFERENCES
        }
        else returnHome()
    }
    BackHandler(enabled = destination != AppDestination.HOME, onBack = navigateBack)
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
                        activeListening = activeListening,
                        onFilterChanged = { chatFilter = it },
                        onActiveCapture = { navigate(AppDestination.LIVE) },
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
                        if (destination == AppDestination.HOME) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                            }
                        } else {
                            IconButton(onClick = navigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAudioInputs = true }) {
                            DeviceGlyph(Modifier.size(24.dp).semantics { contentDescription = "Choose audio device" })
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .navigationBarsPadding()
                    .fillMaxSize()
            ) {
                when (destination) {
                    AppDestination.HOME -> ModeHome(
                        onManual = { destination = AppDestination.MANUAL },
                        onLive = { destination = AppDestination.LIVE },
                        activeListening = activeListening,
                    )
                    AppDestination.MANUAL -> RecorderScreen(
                        state = recorderState,
                        onRecord = onRecord,
                        onStop = onStop,
                        onClear = onClear,
                        onModelSelected = onModelSelected,
                        onProcess = onProcess,
                        onEditTranscript = { transcript ->
                            recorderState.sessionId?.let { onEditTranscript(it, transcript) }
                        },
                        onRenameSpeakers = { names ->
                            recorderState.sessionId?.let { onRenameSpeakers(it, names) }
                        },
                        showAppTitle = false,
                    )
                    AppDestination.LIVE -> ActiveListeningScreen(
                        snapshot = activeListening,
                        queuedTranscriptions = queuedTranscriptions,
                        onEnable = onEnableActiveListening,
                        onPause = onPauseActiveListening,
                        onResume = onResumeActiveListening,
                        onTurnOff = onTurnOffActiveListening,
                    )
                    AppDestination.SESSION -> selectedSession?.let { session ->
                        SessionDetailScreen(
                            session = session,
                            onProcess = { onProcessSession(session.id) },
                            onEditTranscript = { transcript -> onEditTranscript(session.id, transcript) },
                            onRenameSpeakers = { names -> onRenameSpeakers(session.id, names) },
                            onShare = { onShareSession(session) },
                            onDelete = {
                                destination = AppDestination.HOME
                                selectedSessionId = null
                                onDeleteSession(session.id)
                            },
                            onRetryTranscription = { onRetryTranscription(session) },
                        )
                    }
                    AppDestination.PREFERENCES -> PreferencesScreen(
                        darkTheme = darkTheme,
                        selectedModel = recorderState.selectedModel,
                        onDarkThemeChanged = onDarkThemeChanged,
                        onModelSelected = onModelSelected,
                        onAdvanced = { destination = AppDestination.ADVANCED },
                        onExternalDevice = { destination = AppDestination.EXTERNAL_DEVICE },
                        onAiSelection = { destination = AppDestination.AI_SELECTION },
                    )
                    AppDestination.ADVANCED -> AdvancedPreferencesScreen(
                        speechStartThresholdMs = speechStartThresholdMs,
                        minimumTranscriptSpeechSeconds = minimumTranscriptSpeechSeconds,
                        conversationEndSilenceSeconds = silenceSeconds,
                        preRollSeconds = preRollSeconds,
                        minimumTranscriptWords = minimumTranscriptWords,
                        onSpeechStartThresholdChanged = onSpeechStartThresholdChanged,
                        onMinimumTranscriptSpeechChanged = onMinimumTranscriptSpeechChanged,
                        onConversationEndSilenceChanged = onSilenceSecondsChanged,
                        onPreRollChanged = onPreRollChanged,
                        onMinimumTranscriptWordsChanged = onMinimumTranscriptWordsChanged,
                        onResetDefaults = onResetAdvancedDefaults,
                    )
                    AppDestination.EXTERNAL_DEVICE -> ExternalDeviceScreen(
                        endpoint = externalDeviceEndpoint,
                        snapshot = activeListening,
                        onSave = onSaveExternalDevice,
                        onConnect = onConnectExternalDevice,
                        onDisconnect = onTurnOffActiveListening,
                        onForget = onForgetExternalDevice,
                    )
                    AppDestination.AI_SELECTION -> AiSelectionScreen(
                        providers = aiProviders,
                        onProvidersChanged = onAiProvidersChanged,
                    )
                }
            }
        }
    }
    if (showAudioInputs) {
        AudioInputDialog(
            selected = selectedAudioInput,
            xiaoConfigured = externalDeviceEndpoint.host.isNotBlank(),
            onDismiss = { showAudioInputs = false },
            onSelected = { choice ->
                showAudioInputs = false
                onAudioInputSelected(choice)
            },
            onAddDevice = {
                showAudioInputs = false
                destination = AppDestination.EXTERNAL_DEVICE
            },
        )
    }
}

private val AppDestination.title: String
    get() = when (this) {
        AppDestination.HOME -> "Huh?"
        AppDestination.MANUAL -> "Listen Now"
        AppDestination.LIVE -> "Keep an Ear Out"
        AppDestination.SESSION -> "What I Heard"
        AppDestination.PREFERENCES -> "Settings"
        AppDestination.ADVANCED -> "Advanced"
        AppDestination.EXTERNAL_DEVICE -> "External Device"
        AppDestination.AI_SELECTION -> "AI Selection"
    }

@Composable
private fun DeviceGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colors.onPrimary
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.15f), Offset(size.width * 0.28f, size.height * 0.85f), stroke)
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.15f), Offset(size.width * 0.67f, size.height * 0.15f), stroke)
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.85f), Offset(size.width * 0.67f, size.height * 0.85f), stroke)
        drawLine(color, Offset(size.width * 0.67f, size.height * 0.15f), Offset(size.width * 0.67f, size.height * 0.85f), stroke)
        drawCircle(color, radius = stroke * 0.65f, center = Offset(size.width * 0.475f, size.height * 0.75f))
        drawCircle(color, radius = stroke * 0.8f, center = Offset(size.width * 0.82f, size.height * 0.3f))
    }
}

@Composable
private fun ModeHome(
    onManual: () -> Unit,
    onLive: () -> Unit,
    activeListening: ActiveListeningSnapshot,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(0.18f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Never miss what was said.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Private by design · processed on this device",
                fontSize = 13.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
            )
        }
        Spacer(Modifier.weight(0.03f))
        Card(
            modifier = Modifier.fillMaxWidth().weight(0.75f),
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
                    Text("LISTEN NOW", color = MaterialTheme.colors.onPrimary, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("Tap, talk, and save it", color = MaterialTheme.colors.onPrimary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                        .clickable(onClick = onLive).padding(24.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text("KEEP AN EAR OUT", color = MaterialTheme.colors.onSecondary, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text(
                        when (activeListening.state) {
                            ActiveListeningState.WAITING, ActiveListeningState.STARTING -> "ON · Keeping an ear out"
                            ActiveListeningState.RECONNECTING -> "RECONNECTING · External device"
                            ActiveListeningState.LISTENING -> "LISTENING · Conversation in progress"
                            ActiveListeningState.PAUSED -> "PAUSED · Ears off"
                            ActiveListeningState.ERROR -> "NEEDS ATTENTION"
                            ActiveListeningState.OFF -> "Continuous local listening"
                        },
                        color = MaterialTheme.colors.onSecondary,
                        textAlign = TextAlign.End,
                    )
                }
                HuhMark(
                    modifier = Modifier.size(112.dp).align(Alignment.Center),
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.weight(0.04f))
    }
}

@Composable
private fun NavigationDrawer(
    filter: ChatFilter,
    sessions: List<SessionRecord>,
    activeListening: ActiveListeningSnapshot,
    onFilterChanged: (ChatFilter) -> Unit,
    onActiveCapture: () -> Unit,
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
    val showActiveCapture = activeListening.state == ActiveListeningState.LISTENING &&
        filter != ChatFilter.PROCESSED
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HuhMark(Modifier.height(48.dp).fillMaxWidth(0.18f), MaterialTheme.colors.primary)
            Text("Huh?", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
        Divider(Modifier.padding(vertical = 12.dp))
        Text("What I Heard", fontWeight = FontWeight.Bold)
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
        if (filteredSessions.isEmpty() && !showActiveCapture) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (sessions.isEmpty()) "Haven’t heard anything yet." else "No ${filter.label.lowercase()} sessions", fontWeight = FontWeight.Bold)
                Text(
                    if (sessions.isEmpty()) "Completed transcriptions will show up here." else "Try a different filter.",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showActiveCapture) {
                    item(key = "active-capture") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onActiveCapture)
                                .padding(vertical = 10.dp),
                        ) {
                            Text("Conversation in progress", fontWeight = FontWeight.Bold)
                            activeListening.captureStartedAtUtcMillis?.let { startedAt ->
                                Text(
                                    "Started ${SessionMetadata.displayDate(startedAt)}",
                                    fontSize = 13.sp,
                                )
                            }
                            Text("Recording locally · Not yet transcribed", fontSize = 13.sp)
                            Text(
                                "Recording…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Divider()
                    }
                }
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
        TextButton(onClick = onPreferences, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
    }
}

@Composable
private fun SessionDetailScreen(
    session: SessionRecord,
    onProcess: () -> Unit,
    onEditTranscript: (String) -> Unit,
    onRenameSpeakers: (Map<String, String>) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRetryTranscription: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var editTranscript by remember(session.id) { mutableStateOf(false) }
    var nameSpeakers by remember(session.id) { mutableStateOf(false) }
    val speakerIds = session.transcriptSegments.mapNotNull { it.speakerId }.distinct()
    val canEdit = session.transcript.isNotBlank() && session.status !in setOf(
        SessionStatus.TRANSCRIBING,
        SessionStatus.QUEUED,
        SessionStatus.PROCESSING,
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text(session.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            SessionMetadata.displayDate(session.createdAtUtcMillis),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "${SessionMetadata.displayDuration(session.durationMillis)} · ${session.status.displayName}",
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Transcribed locally · ${session.transcriptionModel.displayName} model",
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
        )
        Text(
            session.source.displayName,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colors.primary,
        )
        if (session.transcript.isNotBlank()) {
            SessionContentCard(
                title = "What Was Said",
                text = session.transcript,
                actionLabel = if (canEdit) "Edit" else null,
                onAction = if (canEdit) ({ editTranscript = true }) else null,
                secondaryActionLabel = if (canEdit && speakerIds.isNotEmpty()) "Name speakers" else null,
                onSecondaryAction = if (canEdit && speakerIds.isNotEmpty()) ({ nameSpeakers = true }) else null,
            )
        }
        session.processText?.takeIf(String::isNotBlank)?.let { SessionContentCard("What I Got From It", it) }
        if (session.tags.isNotEmpty()) {
            Text("Tags", modifier = Modifier.padding(top = 20.dp), fontWeight = FontWeight.Bold)
            Text(session.tags.joinToString(" · "), modifier = Modifier.padding(top = 6.dp))
        }
        if (session.status == SessionStatus.TRANSCRIPTION_FAILED) {
            Button(
                onClick = onRetryTranscription,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) { Text("Retry transcription") }
        } else if (session.transcript.isNotBlank() && session.status != SessionStatus.PROCESSED) {
            val processing = session.status in setOf(SessionStatus.QUEUED, SessionStatus.PROCESSING)
            Button(
                onClick = onProcess,
                enabled = !processing,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text(if (processing) "Thinking…" else "Make sense of this")
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
    if (editTranscript) {
        TranscriptEditorDialog(
            transcript = session.transcript,
            discardsInference = session.status == SessionStatus.PROCESSED,
            onDismiss = { editTranscript = false },
            onSave = { edited ->
                editTranscript = false
                onEditTranscript(edited)
            },
        )
    }
    if (nameSpeakers) {
        SpeakerNamesDialog(
            speakerIds = speakerIds,
            discardsInference = session.status == SessionStatus.PROCESSED,
            onDismiss = { nameSpeakers = false },
            onSave = { names ->
                nameSpeakers = false
                onRenameSpeakers(names)
            },
        )
    }
}

@Composable
private fun SessionContentCard(
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Row {
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
                    }
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) { Text(actionLabel) }
                    }
                }
            }
            Text(text, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ActiveListeningScreen(
    snapshot: ActiveListeningSnapshot,
    queuedTranscriptions: Int,
    onEnable: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val active = snapshot.state == ActiveListeningState.LISTENING
        HuhMark(
            Modifier.height(104.dp).fillMaxWidth(0.34f),
            if (active) MaterialTheme.colors.error else MaterialTheme.colors.primary,
        )
        Text(
            when (snapshot.state) {
                ActiveListeningState.OFF -> "Keep an Ear Out"
                ActiveListeningState.STARTING -> "Getting ready…"
                ActiveListeningState.RECONNECTING -> "Reconnecting…"
                ActiveListeningState.WAITING -> "Keeping an ear out"
                ActiveListeningState.LISTENING -> "I’m listening…"
                ActiveListeningState.PAUSED -> "Ears off"
                ActiveListeningState.ERROR -> "Can’t keep an ear out"
            },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            when (snapshot.state) {
                ActiveListeningState.OFF -> "Capture conversations automatically, even when Huh? is in the background."
                ActiveListeningState.STARTING -> if (snapshot.source == ActiveListeningSource.EXTERNAL_DEVICE) {
                    "Connecting to ${snapshot.sourceName ?: "the external device"} on your local network."
                } else "Starting the microphone on this device."
                ActiveListeningState.RECONNECTING ->
                    "Trying to reach ${snapshot.sourceName ?: "the external device"}."
                ActiveListeningState.WAITING -> if (snapshot.source == ActiveListeningSource.EXTERNAL_DEVICE) {
                    "Connected and waiting for audio from ${snapshot.sourceName ?: "the external device"}."
                } else "Listening for conversations on this device."
                ActiveListeningState.LISTENING -> "Conversation in progress"
                ActiveListeningState.PAUSED -> "Active listening is paused. The microphone is not in use."
                ActiveListeningState.ERROR -> snapshot.errorMessage ?: "The microphone is unavailable."
            },
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
        when (snapshot.state) {
            ActiveListeningState.OFF -> Button(
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) { Text("Turn on active listening") }
            ActiveListeningState.STARTING -> Unit
            ActiveListeningState.RECONNECTING ->
                OutlinedButton(onClick = onTurnOff, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text("Turn off")
                }
            ActiveListeningState.WAITING, ActiveListeningState.LISTENING -> {
                Button(onClick = onPause, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Pause") }
                OutlinedButton(onClick = onTurnOff, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Turn off") }
            }
            ActiveListeningState.PAUSED -> {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Resume") }
                OutlinedButton(onClick = onTurnOff, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Turn off") }
            }
            ActiveListeningState.ERROR -> {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Try again") }
                OutlinedButton(onClick = onTurnOff, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Turn off") }
            }
        }
        if (queuedTranscriptions > 0) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Turning speech into words locally", fontWeight = FontWeight.Bold)
                    Text(
                        if (queuedTranscriptions == 1) "1 conversation" else "$queuedTranscriptions conversations queued",
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text("Still listening for new conversations.", modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("Processed on this device", fontWeight = FontWeight.Bold)
                Text(
                    "Audio and transcription stay local. Making sense of a transcript remains your choice.",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (snapshot.state == ActiveListeningState.OFF) {
            Text(
                "A persistent notification remains visible while active.",
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PreferencesScreen(
    darkTheme: Boolean,
    selectedModel: TranscriptionModel,
    onDarkThemeChanged: (Boolean) -> Unit,
    onModelSelected: (TranscriptionModel) -> Unit,
    onAdvanced: () -> Unit,
    onExternalDevice: () -> Unit,
    onAiSelection: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
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
        Text("Used for the next Listen Now or Keep an Ear Out transcription.", modifier = Modifier.padding(top = 8.dp))
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Active Listening")
        OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
            Text("Advanced conversation settings")
        }
        Text(
            "Tune speech detection, conversation timing, and automatic cleanup.",
            modifier = Modifier.padding(top = 8.dp),
        )
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("AI")
        OutlinedButton(onClick = onAiSelection, modifier = Modifier.fillMaxWidth()) {
            Text("AI Selection")
        }
        Text(
            "Choose and prioritize on-device, local-network, or optional third-party methods.",
            modifier = Modifier.padding(top = 8.dp),
        )
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("External Device")
        OutlinedButton(onClick = onExternalDevice, modifier = Modifier.fillMaxWidth()) {
            Text("Configure Huh? Puck")
        }
        Text(
            "Receive audio from a compatible device over your local network.",
            modifier = Modifier.padding(top = 8.dp),
        )
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Privacy")
        Text("Processed on this device", fontWeight = FontWeight.Bold)
        Text("Recording, transcription, and interpretation stay local.", modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Text("Active Listening preferences are saved on this device. Appearance currently resets when the app closes.")
    }
}

@Composable
private fun ExternalDeviceScreen(
    endpoint: ExternalDeviceEndpoint,
    snapshot: ActiveListeningSnapshot,
    onSave: (ExternalDeviceEndpoint) -> Unit,
    onConnect: (ExternalDeviceEndpoint) -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    var host by remember(endpoint.host) { mutableStateOf(endpoint.host) }
    var port by remember(endpoint.port) { mutableStateOf(endpoint.port.toString()) }
    var deviceId by remember(endpoint.expectedDeviceId) { mutableStateOf(endpoint.expectedDeviceId) }
    var displayName by remember(endpoint.displayName) { mutableStateOf(endpoint.displayName) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val externalActive = snapshot.source == ActiveListeningSource.EXTERNAL_DEVICE &&
        snapshot.state != ActiveListeningState.OFF

    fun enteredEndpoint(): ExternalDeviceEndpoint? = runCatching {
        ExternalDeviceEndpoint(
            host = host,
            port = port.toIntOrNull() ?: 0,
            expectedDeviceId = deviceId,
            displayName = displayName,
        ).validated()
    }.onFailure { validationError = it.message }.getOrNull()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f),
            elevation = 0.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Trusted-LAN POC", fontWeight = FontWeight.Bold)
                Text(
                    "Enter the address reported by the device firmware. Audio stays on your local network. " +
                        "Secure BLE pairing is not available until the firmware control contract is finalized.",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Local IP address or host name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit).take(5) },
            label = { Text("TCP port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("Expected device ID (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text(
            "When supplied, the connection is rejected if the device reports a different ID.",
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
        )
        validationError?.let {
            Text(it, color = MaterialTheme.colors.error, modifier = Modifier.padding(top = 12.dp))
        }
        if (externalActive) {
            Text(
                when (snapshot.state) {
                    ActiveListeningState.STARTING -> "Connecting to ${snapshot.sourceName ?: displayName}…"
                    ActiveListeningState.RECONNECTING -> "Reconnecting to ${snapshot.sourceName ?: displayName}…"
                    ActiveListeningState.WAITING -> "Connected and waiting for device audio"
                    ActiveListeningState.LISTENING -> "Receiving a conversation"
                    ActiveListeningState.PAUSED -> "External listening is paused"
                    ActiveListeningState.ERROR -> snapshot.errorMessage ?: "Connection needs attention"
                    ActiveListeningState.OFF -> "Disconnected"
                },
                modifier = Modifier.padding(top = 18.dp),
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Disconnect")
            }
        } else {
            Button(
                onClick = { enteredEndpoint()?.let { validationError = null; onSave(it); onConnect(it) } },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) { Text("Save and connect") }
        }
        OutlinedButton(
            onClick = {
                if (externalActive) onDisconnect()
                onForget()
                host = ""
                port = ExternalDeviceEndpoint.DEFAULT_PORT.toString()
                deviceId = ""
                displayName = "Huh? Puck"
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        ) { Text("Forget device") }
    }
}

@Composable
private fun AdvancedPreferencesScreen(
    speechStartThresholdMs: Float,
    minimumTranscriptSpeechSeconds: Float,
    conversationEndSilenceSeconds: Float,
    preRollSeconds: Float,
    minimumTranscriptWords: Float,
    onSpeechStartThresholdChanged: (Float) -> Unit,
    onMinimumTranscriptSpeechChanged: (Float) -> Unit,
    onConversationEndSilenceChanged: (Float) -> Unit,
    onPreRollChanged: (Float) -> Unit,
    onMinimumTranscriptWordsChanged: (Float) -> Unit,
    onResetDefaults: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text(
            "Timing changes apply while waiting for speech or after the current conversation ends. " +
                "Transcript cleanup applies to pending work.",
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f),
            elevation = 0.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Terms used here", fontWeight = FontWeight.Bold)
                Text(
                    "VAD — Voice Activity Detection",
                    modifier = Modifier.padding(top = 8.dp),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Local analysis that distinguishes human speech from silence and other sounds.",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.74f),
                )
            }
        }
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Conversation start")
        AdvancedSlider(
            title = "${speechStartThresholdMs.toInt()} ms of sustained speech",
            supportingText = "Rejects brief VAD detections while the pre-roll protects the beginning.",
            value = speechStartThresholdMs,
            onValueChange = onSpeechStartThresholdChanged,
            valueRange = 100f..1_000f,
            steps = 8,
        )
        AdvancedSlider(
            title = "${"%.1f".format(preRollSeconds)} seconds of pre-roll",
            supportingText = "Kept in memory and only saved after a conversation is detected.",
            value = preRollSeconds,
            onValueChange = onPreRollChanged,
            valueRange = 0f..5f,
            steps = 9,
        )
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Transcription eligibility")
        AdvancedSlider(
            title = "At least ${minimumTranscriptSpeechSeconds.toInt()} seconds of detected speech",
            supportingText = "Only cumulative VAD-positive speech counts; silence never makes a capture eligible.",
            value = minimumTranscriptSpeechSeconds,
            onValueChange = onMinimumTranscriptSpeechChanged,
            valueRange = 1f..15f,
            steps = 13,
        )
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Conversation ending")
        AdvancedSlider(
            title = "End after ${conversationEndSilenceSeconds.toInt()} seconds without speech",
            supportingText = "Any detected speech resets this continuous-silence timer.",
            value = conversationEndSilenceSeconds,
            onValueChange = onConversationEndSilenceChanged,
            valueRange = 5f..60f,
            steps = 10,
        )
        Divider(Modifier.padding(vertical = 20.dp))
        PreferenceHeading("Transcript cleanup")
        AdvancedSlider(
            title = if (minimumTranscriptWords.toInt() == 0) "Keep transcripts of any length"
            else "Discard transcripts with fewer than ${minimumTranscriptWords.toInt()} words",
            supportingText = "This does not control Gemma. Make sense of this always remains user-controlled.",
            value = minimumTranscriptWords,
            onValueChange = onMinimumTranscriptWordsChanged,
            valueRange = 0f..20f,
            steps = 19,
        )
        Divider(Modifier.padding(vertical = 20.dp))
        Text("Timing metadata stays on this device and helps tune these defaults later.")
        OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
        ) { Text("Reset to defaults") }
    }
}

@Composable
private fun AdvancedSlider(
    title: String,
    supportingText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Text(title, fontWeight = FontWeight.Bold)
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    Text(supportingText)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun PreferenceHeading(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
}
