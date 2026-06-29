package com.timetrack.ui

import com.timetrack.util.WeekUtils

/** "8", "7,5" – svensk formattering utan onödiga decimaler. */
fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(WeekUtils.SV, "%.1f", value)

/** "8 h" */
fun formatHoursLabel(value: Double): String = "${formatHours(value)} h"

/** Tolkar fält-input där både "," och "." accepteras som decimaltecken. */
fun parseHours(raw: String): Double =
    raw.trim().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

/** Tillåter bara siffror och ett decimaltecken i timfält. */
fun sanitizeHourInput(raw: String): String {
    val cleaned = raw.replace('.', ',').filter { it.isDigit() || it == ',' }
    val firstComma = cleaned.indexOf(',')
    if (firstComma == -1) return cleaned
    val intPart = cleaned.substring(0, firstComma)
    val decPart = cleaned.substring(firstComma + 1).filter { it.isDigit() }
    return "$intPart,$decPart"
}
