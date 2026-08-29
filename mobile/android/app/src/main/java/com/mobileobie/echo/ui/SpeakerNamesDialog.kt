package com.mobileobie.echo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobileobie.echo.transcription.SpeakerLabels

@Composable
internal fun SpeakerNamesDialog(
    speakerIds: List<String>,
    discardsInference: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    val initialNames = speakerIds.map(SpeakerLabels::displayName)
    var names by rememberSaveable(speakerIds) { mutableStateOf(initialNames) }
    val normalized = names.map(String::trim)
    val valid = normalized.all { name ->
        name.isNotEmpty() && name.length <= SpeakerLabels.MAX_NAME_LENGTH &&
            ':' !in name && '\n' !in name && '\r' !in name
    } && normalized.distinctBy(String::lowercase).size == normalized.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name speakers") },
        text = {
            Column {
                Text(
                    if (discardsInference) {
                        "Saving names removes the current inference. You can run Make sense of this again."
                    } else {
                        "Names replace speaker labels in this transcript."
                    },
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                speakerIds.forEachIndexed { index, speakerId ->
                    OutlinedTextField(
                        value = names[index],
                        onValueChange = { updated ->
                            names = names.toMutableList().also { it[index] = updated }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text(SpeakerLabels.displayName(speakerId)) },
                        singleLine = true,
                    )
                }
                if (!valid) {
                    Text(
                        "Use a distinct name of 1–${SpeakerLabels.MAX_NAME_LENGTH} characters without colons.",
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(speakerIds.zip(normalized).toMap()) },
                enabled = valid && normalized != initialNames,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
