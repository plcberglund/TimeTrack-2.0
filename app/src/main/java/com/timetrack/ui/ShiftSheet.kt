package com.timetrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetrack.ui.components.Gap
import com.timetrack.ui.components.InputField
import com.timetrack.ui.components.PrimaryButton
import com.timetrack.ui.components.QuickChips
import com.timetrack.ui.components.SectionLabel
import com.timetrack.ui.components.WidthGap
import com.timetrack.ui.theme.TT
import com.timetrack.util.WeekUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftSheet(
    target: SheetTarget,
    companySuggestions: List<String>,
    workplaceSuggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (company: String, workplace: String, note: String, hours: Double, ob: Double) -> Unit,
    onDelete: () -> Unit,
) {
    val editing = target.shift != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var company by remember { mutableStateOf(target.shift?.company ?: "") }
    var workplace by remember { mutableStateOf(target.shift?.workplace ?: "") }
    var note by remember { mutableStateOf(target.shift?.note ?: "") }
    var hoursText by remember { mutableStateOf(target.shift?.hours?.let { formatHours(it) } ?: "") }
    var obText by remember { mutableStateOf(target.shift?.obHours?.takeIf { it > 0 }?.let { formatHours(it) } ?: "") }

    val canSave = company.isNotBlank() && parseHours(hoursText) > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TT.card,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
        ) {
            Text(
                if (editing) "Redigera pass" else "Nytt arbetspass",
                color = TT.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${WeekUtils.dayName(target.date)} ${WeekUtils.dayMonth(target.date)}",
                color = TT.textSecondary,
                fontSize = 15.sp,
            )

            Gap(20)
            SectionLabel("Företag")
            Gap(8)
            InputField(company, { company = it }, "Skriv företag")
            if (companySuggestions.isNotEmpty()) {
                Gap(10)
                QuickChips(companySuggestions, onPick = { company = it })
            }

            Gap(18)
            SectionLabel("Arbetsplats / plats")
            Gap(8)
            InputField(workplace, { workplace = it }, "Skriv arbetsplats")
            if (workplaceSuggestions.isNotEmpty()) {
                Gap(10)
                QuickChips(workplaceSuggestions, onPick = { workplace = it })
            }

            Gap(18)
            SectionLabel("Anteckning")
            Gap(8)
            InputField(note, { note = it }, "Valfri anteckning", singleLine = false)

            Gap(18)
            SectionLabel("Antal timmar")
            Gap(8)
            InputField(
                hoursText,
                { hoursText = sanitizeHourInput(it) },
                "0",
                keyboardType = KeyboardType.Number,
            )

            Gap(18)
            SectionLabel("OB-timmar")
            Gap(8)
            InputField(
                obText,
                { obText = sanitizeHourInput(it) },
                "0",
                keyboardType = KeyboardType.Number,
            )
            Gap(6)
            Text(
                "Räknas separat – läggs inte ovanpå de vanliga timmarna.",
                color = TT.textTertiary,
                fontSize = 13.sp,
            )

            if (editing) {
                Gap(18)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDelete)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = TT.danger,
                        modifier = Modifier.size(22.dp),
                    )
                    WidthGap(8)
                    Text("Ta bort pass", color = TT.danger, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            Gap(20)
            PrimaryButton("Spara", enabled = canSave) {
                onSave(company, workplace, note, parseHours(hoursText), parseHours(obText))
            }
            Box(Modifier.size(8.dp))
        }
    }
}
