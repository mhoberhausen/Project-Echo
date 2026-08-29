package com.mobileobie.echo.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileobie.echo.settings.AiProviderConfig
import com.mobileobie.echo.settings.AiProviderKind
import com.mobileobie.echo.settings.AudioInputChoice

@Composable
internal fun AudioInputDialog(
    selected: AudioInputChoice,
    xiaoConfigured: Boolean,
    onDismiss: () -> Unit,
    onSelected: (AudioInputChoice) -> Unit,
    onAddDevice: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio device") },
        text = {
            Column {
                AudioInputChoice.entries.forEach { choice ->
                    val enabled = choice == AudioInputChoice.PHONE ||
                        (choice == AudioInputChoice.XIAO && xiaoConfigured)
                    val supporting = when (choice) {
                        AudioInputChoice.PHONE -> "Built-in microphone"
                        AudioInputChoice.BLUETOOTH -> "Setup required · capture support is coming"
                        AudioInputChoice.XIAO -> if (xiaoConfigured) "Configured on your local network" else "Setup required"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.52f).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == choice,
                            onClick = { if (enabled) onSelected(choice) },
                            enabled = enabled,
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(choice.label, fontWeight = FontWeight.Bold)
                            Text(supporting, color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAddDevice) { Text("Add device") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun AiSelectionScreen(
    providers: List<AiProviderConfig>,
    onProvidersChanged: (List<AiProviderConfig>) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(
            "Enabled methods are tried in this order. Drag a card up or down to change priority.",
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
        )
        providers.forEachIndexed { index, provider ->
            AiProviderCard(
                provider = provider,
                onEnabled = { enabled ->
                    onProvidersChanged(providers.toMutableList().also { it[index] = provider.copy(enabled = enabled) })
                },
                onMove = { direction ->
                    val target = (index + direction).coerceIn(providers.indices)
                    if (target != index) {
                        onProvidersChanged(providers.toMutableList().also {
                            val moved = it.removeAt(index)
                            it.add(target, moved)
                        })
                    }
                },
            )
        }
        OutlinedButton(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        ) { Text("Add AI method") }
        Text(
            "Bundled Gemma and OpenAI-compatible LAN endpoints can process transcripts. LAN methods use " +
                "the first suitable instruction model reported by the server. Third-party methods remain configuration-only.",
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
        )
    }
    if (adding) AddAiMethodDialog(
        onDismiss = { adding = false },
        onAdd = { provider ->
            adding = false
            onProvidersChanged(providers + provider)
        },
    )
}

@Composable
private fun AiProviderCard(
    provider: AiProviderConfig,
    onEnabled: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { dragged += it },
            onDragStopped = {
                if (dragged > 32f) onMove(1) else if (dragged < -32f) onMove(-1)
                dragged = 0f
            },
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Drag to reorder ${provider.name}",
                modifier = Modifier.padding(end = 14.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(provider.name, fontWeight = FontWeight.Bold)
                Text(provider.kind.label)
                provider.endpoint.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f))
                }
                if (!provider.available) {
                    Text("Connector not installed", color = MaterialTheme.colors.secondary)
                } else if (provider.kind == AiProviderKind.LAN) {
                    Text("OpenAI-compatible connector", color = MaterialTheme.colors.primary)
                }
            }
            Switch(checked = provider.enabled, onCheckedChange = onEnabled)
        }
    }
}

@Composable
private fun AddAiMethodDialog(onDismiss: () -> Unit, onAdd: (AiProviderConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AiProviderKind.LAN) }
    val valid = name.isNotBlank() && endpoint.startsWith("http")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add AI method") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AiProviderKind.LAN, AiProviderKind.THIRD_PARTY).forEach { option ->
                        OutlinedButton(onClick = { kind = option }) {
                            Text(if (kind == option) "✓ ${option.label}" else option.label)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Text(
                    if (kind == AiProviderKind.LAN) "Example: http://192.168.1.20:11434" else "Credentials will be requested when this connector is implemented.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(AiProviderConfig.added(name, kind, endpoint)) },
                enabled = valid,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
