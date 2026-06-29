package com.timetrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timetrack.data.DayStatus
import com.timetrack.data.Shift
import com.timetrack.ui.components.AppHeader
import com.timetrack.ui.components.Gap
import com.timetrack.ui.components.ModeTabs
import com.timetrack.ui.components.WidthGap
import com.timetrack.ui.theme.TT
import com.timetrack.util.WeekUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SheetTarget(val date: LocalDate, val shift: Shift?)

@Composable
fun TimeTrackRoot(vm: TimeTrackViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mode by vm.viewMode.collectAsState()
    val name by vm.userName.collectAsState()
    val week by vm.weekState.collectAsState()
    val month by vm.monthState.collectAsState()
    val companySug by vm.companySuggestions.collectAsState()
    val workplaceSug by vm.workplaceSuggestions.collectAsState()

    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = TT.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (mode == ViewMode.WEEK) {
                SendBar(week.totalHours) {
                    scope.launch {
                        val res = vm.exportCurrentWeek(context)
                        if (res.empty) {
                            snackbar.showSnackbar("Inget att skicka för vecka ${res.week} ännu")
                        } else {
                            vm.share(context, res)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AppHeader(name) { showSettings = true }
            ModeTabs(
                selectedMonth = mode == ViewMode.MONTH,
                onWeek = { vm.setViewMode(ViewMode.WEEK) },
                onMonth = { vm.setViewMode(ViewMode.MONTH) },
            )
            Gap(8)
            when (mode) {
                ViewMode.WEEK -> WeekPane(
                    state = week,
                    onPrev = vm::prevWeek,
                    onNext = vm::nextWeek,
                    onAdd = { date -> sheet = SheetTarget(date, null) },
                    onEdit = { shift -> sheet = SheetTarget(LocalDate.ofEpochDay(shift.date), shift) },
                    onStatus = { date, status, current -> vm.toggleStatus(date, status, current) },
                )
                ViewMode.MONTH -> MonthPane(month)
            }
        }
    }

    sheet?.let { target ->
        ShiftSheet(
            target = target,
            companySuggestions = companySug,
            workplaceSuggestions = workplaceSug,
            onDismiss = { sheet = null },
            onSave = { company, workplace, note, hours, ob ->
                vm.saveShift(target.shift?.id ?: 0L, target.date, company, workplace, note, hours, ob)
                sheet = null
            },
            onDelete = {
                target.shift?.let { vm.deleteShift(it) }
                sheet = null
            },
            onDeleteCompanySuggestion = vm::removeCompanySuggestion,
            onDeleteWorkplaceSuggestion = vm::removeWorkplaceSuggestion,
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentName = name,
            onSave = { vm.setUserName(it); showSettings = false },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun WeekPane(
    state: WeekUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAdd: (LocalDate) -> Unit,
    onEdit: (Shift) -> Unit,
    onStatus: (LocalDate, DayStatus, DayStatus?) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WeekNavigator(week = state.week, range = state.rangeLabel, onPrev = onPrev, onNext = onNext)
        WeekChips(state = state, onPickPrev = onPrev)
        Gap(8)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                DayCard(day = day, onAdd = { onAdd(day.date) }, onEdit = onEdit, onStatus = onStatus)
            }
        }
    }
}

@Composable
private fun WeekNavigator(week: Int, range: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavSquare(Icons.Filled.KeyboardArrowLeft, "Föregående vecka", onPrev)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("V. $week", color = TT.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(range, color = TT.textSecondary, fontSize = 14.sp)
        }
        NavSquare(Icons.Filled.KeyboardArrowRight, "Nästa vecka", onNext)
    }
}

@Composable
private fun NavSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(TT.card, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = TT.textPrimary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun WeekChips(state: WeekUiState, onPickPrev: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Aktuell vecka (vald)
        Box(
            Modifier
                .weight(1f)
                .height(64.dp)
                .background(TT.orange, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text("V. ${state.week}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(formatHoursLabel(state.totalHours), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
        }
        // Föregående vecka
        Box(
            Modifier
                .weight(1f)
                .height(64.dp)
                .background(TT.card, RoundedCornerShape(14.dp))
                .clickable(onClick = onPickPrev)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text("V. ${state.prevWeek}", color = TT.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val sub = if (state.prevHasData) {
                    buildString {
                        append(formatHoursLabel(state.prevHours))
                        if (state.prevOb > 0) append(" · OB ${formatHours(state.prevOb)}")
                    }
                } else "–"
                Text(sub, color = TT.textTertiary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DayCard(
    day: DayUi,
    onAdd: () -> Unit,
    onEdit: (Shift) -> Unit,
    onStatus: (LocalDate, DayStatus, DayStatus?) -> Unit,
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .background(TT.card, RoundedCornerShape(18.dp))
        .then(if (day.isToday) Modifier.border(1.5.dp, TT.orange, RoundedCornerShape(18.dp)) else Modifier)
        .padding(16.dp)

    Column(cardModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(WeekUtils.dayName(day.date), color = TT.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (day.isToday) {
                WidthGap(8)
                TodayBadge()
            }
            Box(Modifier.weight(1f))
            Text(WeekUtils.dayMonth(day.date), color = TT.textSecondary, fontSize = 14.sp)
            WidthGap(12)
            Text(formatHoursLabel(day.hours), color = TT.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Gap(12)

        day.shifts.forEach { shift ->
            ShiftRow(shift = shift, onClick = { onEdit(shift) })
            Gap(8)
        }

        AddPassButton(onClick = onAdd)

        if (day.shifts.isEmpty()) {
            Gap(10)
            StatusRow(selected = day.status, onStatus = { status -> onStatus(day.date, status, day.status) })
        }
    }
}

@Composable
private fun TodayBadge() {
    Box(
        Modifier
            .background(TT.orangeSoft, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text("IDAG", color = TT.orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShiftRow(shift: Shift, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TT.cardElevated, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                shift.company.ifBlank { "(utan företag)" },
                color = TT.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            if (shift.workplace.isNotBlank()) {
                Text(shift.workplace, color = TT.textSecondary, fontSize = 13.sp)
            }
            if (shift.note.isNotBlank()) {
                Text(shift.note, color = TT.textTertiary, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatHoursLabel(shift.hours), color = TT.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (shift.obHours > 0) {
                Text("OB ${formatHours(shift.obHours)}", color = TT.orange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AddPassButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .dashedBorder(TT.orange, 12.dp, 1.5.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = TT.orange, modifier = Modifier.size(20.dp))
            WidthGap(6)
            Text("Lägg till pass", color = TT.orange, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatusRow(selected: DayStatus?, onStatus: (DayStatus) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DayStatus.entries.forEach { status ->
            val isSel = selected == status
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (isSel) TT.orangeSoft else TT.cardElevated, RoundedCornerShape(12.dp))
                    .then(if (isSel) Modifier.border(1.dp, TT.orange, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onStatus(status) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    status.label,
                    color = if (isSel) TT.orange else TT.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SendBar(totalHours: Double, onSend: () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(TT.cardElevated, RoundedCornerShape(18.dp))
                .clickable(onClick = onSend)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = TT.orange)
            WidthGap(12)
            Text("Skicka rapport", color = TT.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.weight(1f))
            Text(formatHoursLabel(totalHours), color = TT.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MonthPane(state: MonthUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${state.year}", color = TT.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("OB totalt: ${formatHoursLabel(state.totalOb)}", color = TT.textSecondary, fontSize = 14.sp)
            }
            Text("${formatHoursLabel(state.totalHours)} hittills", color = TT.orange, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Gap(16)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.months, key = { it.first.toEpochDay() }) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(TT.card, RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.label, color = TT.textPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatHoursLabel(m.hours), color = TT.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        if (m.obHours > 0) {
                            Text("OB ${formatHoursLabel(m.obHours)}", color = TT.orange, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (state.months.isEmpty()) {
                item {
                    Text(
                        "Ingen rapporterad tid ännu i år.",
                        color = TT.textTertiary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp): Modifier =
    this.drawBehind {
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
        )
        drawRoundRect(
            color = color,
            style = stroke,
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        )
    }
