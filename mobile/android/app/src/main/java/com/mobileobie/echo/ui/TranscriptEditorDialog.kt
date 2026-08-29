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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
internal fun TranscriptEditorDialog(
    transcript: String,
    discardsInference: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(transcript) { mutableStateOf(transcript) }
    val normalized = draft.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit transcript") },
        text = {
            Column {
                if (discardsInference) {
                    Text(
                        "Saving changes removes the current inference. You can run Make sense of this again.",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What Was Said") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 7,
                    maxLines = 14,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(normalized) },
                enabled = normalized.isNotEmpty() && normalized != transcript.trim(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
