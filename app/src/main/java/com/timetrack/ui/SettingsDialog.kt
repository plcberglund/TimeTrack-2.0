package com.timetrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.timetrack.ui.components.Gap
import com.timetrack.ui.components.InputField
import com.timetrack.ui.components.SectionLabel
import com.timetrack.ui.theme.TT

@Composable
fun SettingsDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TT.card,
        title = { Text("Inställningar", color = TT.textPrimary) },
        text = {
            Column {
                SectionLabel("Ditt namn")
                Gap(8)
                InputField(name, { name = it }, "Förnamn Efternamn")
                Gap(12)
                Text(
                    "Namnet visas högst upp och i Excel-rapporten du skickar.",
                    color = TT.textTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text("Spara", color = TT.orange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt", color = TT.textSecondary)
            }
        },
    )
}
