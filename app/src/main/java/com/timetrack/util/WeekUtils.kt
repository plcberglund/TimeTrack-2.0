package com.timetrack.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

object WeekUtils {
    val SV: Locale = Locale("sv", "SE")

    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", SV)

    /** Måndagen i samma ISO-vecka som [date]. */
    fun mondayOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun weekDays(monday: LocalDate): List<LocalDate> =
        (0..6).map { monday.plusDays(it.toLong()) }

    fun isoWeek(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun weekBasedYear(date: LocalDate): Int = date.get(IsoFields.WEEK_BASED_YEAR)

    /** "Måndag" */
    fun dayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, SV).replaceFirstChar { it.uppercase() }

    /** "29 juni" */
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)

    /** "29 juni – 5 juli" */
    fun rangeLabel(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return "${dayMonth(monday)} – ${dayMonth(sunday)}"
    }

    /** "Juni 2026" */
    fun monthLabel(yearMonthFirst: LocalDate): String {
        val month = yearMonthFirst.month.getDisplayName(TextStyle.FULL, SV)
            .replaceFirstChar { it.uppercase() }
        return "$month ${yearMonthFirst.year}"
    }
}
