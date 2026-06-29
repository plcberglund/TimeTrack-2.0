package com.timetrack.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetrack.ui.theme.TT

@Composable
fun AppHeader(name: String, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "T I M E T R A C K",
                color = TT.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
            Text(
                name.ifBlank { "Ditt namn" },
                color = if (name.isBlank()) TT.textTertiary else TT.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            Modifier
                .size(42.dp)
                .background(TT.card, CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Inställningar", tint = TT.textPrimary)
        }
    }
}

@Composable
fun ModeTabs(selectedMonth: Boolean, onWeek: () -> Unit, onMonth: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabPill("Vecka", selected = !selectedMonth, modifier = Modifier.weight(1f), onClick = onWeek)
        TabPill("Månad", selected = selectedMonth, modifier = Modifier.weight(1f), onClick = onMonth)
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (selected) TT.pill else TT.card, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) TT.pillText else TT.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TT.field, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = TT.textPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(TT.orange),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, color = TT.textTertiary, fontSize = 16.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TT.textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun QuickChips(
    options: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((String) -> Unit)? = null,
) {
    if (options.isEmpty()) return
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            Box(
                Modifier
                    .background(TT.cardElevated, RoundedCornerShape(20.dp))
                    .combinedClickable(
                        onClick = { onPick(option) },
                        onLongClick = if (onDelete != null) {
                            { pendingDelete = option }
                        } else null,
                    )
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(option, color = TT.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = TT.card,
            title = { Text("Ta bort snabbknapp", color = TT.textPrimary) },
            text = { Text("Vill du ta bort \"$toDelete\"?", color = TT.textSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(toDelete); pendingDelete = null }) {
                    Text("Ta bort", color = TT.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Avbryt", color = TT.textSecondary)
                }
            },
        )
    }
}

/** Liten avrundad initial-bricka, t.ex. "TF". */
@Composable
fun InitialBadge(text: String) {
    Box(
        Modifier
            .size(44.dp)
            .border(1.dp, TT.divider, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TT.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (enabled) TT.orange else TT.cardElevated, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) androidx.compose.ui.graphics.Color.White else TT.textTertiary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Gap(height: Int) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun WidthGap(width: Int) {
    Spacer(Modifier.width(width.dp))
}
