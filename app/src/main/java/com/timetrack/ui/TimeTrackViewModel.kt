package com.timetrack.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timetrack.data.DayMark
import com.timetrack.data.DayStatus
import com.timetrack.data.Shift
import com.timetrack.data.Suggestion
import com.timetrack.data.TimeTrackRepository
import com.timetrack.util.DayReport
import com.timetrack.util.ReportExporter
import com.timetrack.util.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

enum class ViewMode { WEEK, MONTH }

data class DayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val status: DayStatus?,
    val shifts: List<Shift>,
) {
    val hours: Double get() = shifts.sumOf { it.hours }
    val obHours: Double get() = shifts.sumOf { it.obHours }
}

data class WeekUiState(
    val monday: LocalDate,
    val week: Int,
    val year: Int,
    val rangeLabel: String,
    val days: List<DayUi>,
    val totalHours: Double,
    val totalOb: Double,
    val prevWeek: Int,
    val prevHours: Double,
    val prevOb: Double,
    val prevHasData: Boolean,
)

data class MonthSummary(val first: LocalDate, val label: String, val hours: Double, val obHours: Double)

data class MonthUiState(
    val year: Int,
    val totalHours: Double,
    val totalOb: Double,
    val months: List<MonthSummary>,
)

data class ExportResult(val file: File, val week: Int, val year: Int, val userName: String, val empty: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class TimeTrackViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TimeTrackRepository(app)

    private val _monday = MutableStateFlow(WeekUtils.mondayOf(LocalDate.now()))
    val monday: StateFlow<LocalDate> = _monday.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.WEEK)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    val userName: StateFlow<String> = repo.settings.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companySuggestions: StateFlow<List<String>> = repo.observeCompanySuggestions()
        .map { list -> list.map(Suggestion::value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workplaceSuggestions: StateFlow<List<String>> = repo.observeWorkplaceSuggestions()
        .map { list -> list.map(Suggestion::value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekState: StateFlow<WeekUiState> = _monday.flatMapLatest { monday ->
        val prevMonday = monday.minusWeeks(1)
        combine(
            repo.observeShifts(monday, monday.plusDays(6)),
            repo.observeDayMarks(monday, monday.plusDays(6)),
            repo.observeShifts(prevMonday, prevMonday.plusDays(6)),
            repo.observeDayMarks(prevMonday, prevMonday.plusDays(6)),
        ) { shifts, marks, prevShifts, prevMarks ->
            buildWeek(monday, shifts, marks, prevShifts, prevMarks)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        buildWeek(WeekUtils.mondayOf(LocalDate.now()), emptyList(), emptyList(), emptyList(), emptyList()),
    )

    val monthState: StateFlow<MonthUiState> = repo.observeAllShifts()
        .map { buildMonths(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MonthUiState(LocalDate.now().year, 0.0, 0.0, emptyList()),
        )

    // ---- Aktioner ----

    fun setViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun prevWeek() { _monday.value = _monday.value.minusWeeks(1) }
    fun nextWeek() { _monday.value = _monday.value.plusWeeks(1) }
    fun goToWeekOf(date: LocalDate) { _monday.value = WeekUtils.mondayOf(date) }

    fun saveShift(
        id: Long,
        date: LocalDate,
        company: String,
        workplace: String,
        note: String,
        hours: Double,
        obHours: Double,
    ) = viewModelScope.launch {
        repo.clearDayStatus(date) // ett pass ersätter en heldagsmarkering
        repo.saveShift(
            Shift(
                id = id,
                date = date.toEpochDay(),
                company = company.trim(),
                workplace = workplace.trim(),
                note = note.trim(),
                hours = hours,
                obHours = obHours,
            )
        )
    }

    fun deleteShift(shift: Shift) = viewModelScope.launch { repo.deleteShift(shift) }

    fun toggleStatus(date: LocalDate, status: DayStatus, current: DayStatus?) = viewModelScope.launch {
        if (current == status) repo.clearDayStatus(date) else repo.setDayStatus(date, status)
    }

    fun setUserName(name: String) = viewModelScope.launch { repo.settings.setUserName(name.trim()) }

    fun removeCompanySuggestion(value: String) =
        viewModelScope.launch { repo.deleteSuggestion(Suggestion.FIELD_COMPANY, value) }

    fun removeWorkplaceSuggestion(value: String) =
        viewModelScope.launch { repo.deleteSuggestion(Suggestion.FIELD_WORKPLACE, value) }

    suspend fun exportCurrentWeek(context: Context): ExportResult {
        val state = weekState.value
        val name = userName.value
        val reports = state.days
            .filter { it.shifts.isNotEmpty() || it.status != null }
            .map { DayReport(it.date, it.status, it.shifts) }
        val file = withContext(Dispatchers.IO) {
            ReportExporter.buildFile(context, name, state.monday, reports)
        }
        return ExportResult(file, state.week, state.year, name, reports.isEmpty())
    }

    fun share(context: Context, result: ExportResult) {
        ReportExporter.shareToGmail(context, result.file, result.week, result.year, result.userName)
    }

    // ---- Bygglogik ----

    private fun buildWeek(
        monday: LocalDate,
        shifts: List<Shift>,
        marks: List<DayMark>,
        prevShifts: List<Shift>,
        prevMarks: List<DayMark>,
    ): WeekUiState {
        val today = LocalDate.now()
        val shiftsByDay = shifts.groupBy { LocalDate.ofEpochDay(it.date) }
        val markByDay = marks.associateBy { LocalDate.ofEpochDay(it.date) }

        val days = WeekUtils.weekDays(monday).map { date ->
            val dayShifts = shiftsByDay[date].orEmpty()
            val status = if (dayShifts.isEmpty()) DayStatus.fromName(markByDay[date]?.status) else null
            DayUi(date = date, isToday = date == today, status = status, shifts = dayShifts)
        }

        val prevMonday = monday.minusWeeks(1)
        return WeekUiState(
            monday = monday,
            week = WeekUtils.isoWeek(monday),
            year = WeekUtils.weekBasedYear(monday),
            rangeLabel = WeekUtils.rangeLabel(monday),
            days = days,
            totalHours = days.sumOf { it.hours },
            totalOb = days.sumOf { it.obHours },
            prevWeek = WeekUtils.isoWeek(prevMonday),
            prevHours = prevShifts.sumOf { it.hours },
            prevOb = prevShifts.sumOf { it.obHours },
            prevHasData = prevShifts.isNotEmpty() || prevMarks.isNotEmpty(),
        )
    }

    private fun buildMonths(allShifts: List<Shift>): MonthUiState {
        val year = LocalDate.now().year
        val ofYear = allShifts.filter { LocalDate.ofEpochDay(it.date).year == year }
        val byMonth = ofYear.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }

        val months = byMonth.entries
            .sortedByDescending { it.key }
            .map { (ym, list) ->
                val first = ym.atDay(1)
                MonthSummary(
                    first = first,
                    label = WeekUtils.monthLabel(first),
                    hours = list.sumOf { it.hours },
                    obHours = list.sumOf { it.obHours },
                )
            }

        return MonthUiState(
            year = year,
            totalHours = ofYear.sumOf { it.hours },
            totalOb = ofYear.sumOf { it.obHours },
            months = months,
        )
    }
}
