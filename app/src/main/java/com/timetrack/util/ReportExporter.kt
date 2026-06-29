package com.timetrack.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.timetrack.data.DayStatus
import com.timetrack.data.Shift
import java.io.File
import java.time.LocalDate

/** En dag i rapporten. */
data class DayReport(
    val date: LocalDate,
    val status: DayStatus?,
    val shifts: List<Shift>,
)

object ReportExporter {

    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    // Färgpalett (matchar appens orange-tema)
    private const val ORANGE = "F26A0E"
    private const val ORANGE_SOFT = "FCEAD9"
    private const val GRAY_HEAD = "EFEFEF"
    private const val GRAY_SOFT = "F7F7F7"

    /** Bygger Excel-filen och returnerar den. */
    fun buildFile(
        context: Context,
        userName: String,
        monday: LocalDate,
        days: List<DayReport>,
    ): File {
        val week = WeekUtils.isoWeek(monday)
        val year = WeekUtils.weekBasedYear(monday)
        val w = XlsxWriter("Vecka $week")

        w.colWidth(1, 24.0)
        w.colWidth(2, 24.0)
        w.colWidth(3, 30.0)
        w.colWidth(4, 11.0)
        w.colWidth(5, 13.0)

        // Stilar
        val title = w.style(XlsxWriter.Style(bold = true, fontSize = 22, fontColor = "FF$ORANGE", hAlign = "left"))
        val name = w.style(XlsxWriter.Style(bold = true, fontSize = 12, fontColor = "FF333333", hAlign = "left"))
        val sub = w.style(XlsxWriter.Style(fontSize = 11, fontColor = "FF888888", hAlign = "left"))

        val dayHead = w.style(XlsxWriter.Style(bold = true, fontSize = 12, fontColor = "FFFFFFFF", fill = ORANGE, border = true, borderColor = "FF$ORANGE", hAlign = "left"))
        val dayHeadNum = w.style(XlsxWriter.Style(bold = true, fontSize = 12, fontColor = "FFFFFFFF", fill = ORANGE, border = true, borderColor = "FF$ORANGE", hAlign = "right"))

        val colHead = w.style(XlsxWriter.Style(bold = true, fontSize = 10, fontColor = "FF555555", fill = GRAY_HEAD, border = true, hAlign = "left"))
        val colHeadNum = w.style(XlsxWriter.Style(bold = true, fontSize = 10, fontColor = "FF555555", fill = GRAY_HEAD, border = true, hAlign = "right"))

        val cell = w.style(XlsxWriter.Style(fontSize = 11, fontColor = "FF333333", border = true, hAlign = "left", wrap = true))
        val cellNum = w.style(XlsxWriter.Style(fontSize = 11, fontColor = "FF333333", border = true, hAlign = "right"))
        val statusCell = w.style(XlsxWriter.Style(bold = true, fontSize = 11, fontColor = "FF888888", fill = GRAY_SOFT, border = true, hAlign = "left"))

        val totalLabel = w.style(XlsxWriter.Style(bold = true, fontSize = 13, fontColor = "FF333333", fill = ORANGE_SOFT, border = true, borderColor = "FF$ORANGE", hAlign = "right"))
        val totalNum = w.style(XlsxWriter.Style(bold = true, fontSize = 13, fontColor = "FF$ORANGE", fill = ORANGE_SOFT, border = true, borderColor = "FF$ORANGE", hAlign = "right"))

        var row = 1

        // Topprubrik
        w.text(row, 1, "TIDRAPPORT", title)
        w.merge(row, 1, row, 5)
        w.rowHeight(row, 30.0)
        row++
        if (userName.isNotBlank()) {
            w.text(row, 1, userName, name)
            w.merge(row, 1, row, 5)
            row++
        }
        w.text(row, 1, "Vecka $week, $year  ·  ${WeekUtils.rangeLabel(monday)}", sub)
        w.merge(row, 1, row, 5)
        row++
        row++ // tom rad

        var weekHours = 0.0
        var weekOb = 0.0

        for (day in days) {
            val dayHours = day.shifts.sumOf { it.hours }
            val dayOb = day.shifts.sumOf { it.obHours }
            weekHours += dayHours
            weekOb += dayOb

            val heading = "${WeekUtils.dayName(day.date)}  ${WeekUtils.dayMonth(day.date)}"

            if (day.shifts.isNotEmpty()) {
                // Dag-header
                w.text(row, 1, heading, dayHead)
                w.merge(row, 1, row, 3)
                w.number(row, 4, dayHours, dayHeadNum)
                w.number(row, 5, dayOb, dayHeadNum)
                w.rowHeight(row, 20.0)
                row++

                // Kolumnrubriker
                w.text(row, 1, "Företag", colHead)
                w.text(row, 2, "Arbetsplats", colHead)
                w.text(row, 3, "Anteckning", colHead)
                w.text(row, 4, "Timmar", colHeadNum)
                w.text(row, 5, "OB-tim", colHeadNum)
                row++

                // Pass
                for (s in day.shifts) {
                    w.text(row, 1, s.company, cell)
                    w.text(row, 2, s.workplace, cell)
                    w.text(row, 3, s.note, cell)
                    w.number(row, 4, s.hours, cellNum)
                    w.number(row, 5, s.obHours, cellNum)
                    row++
                }
            } else if (day.status != null) {
                w.text(row, 1, heading, dayHead)
                w.merge(row, 1, row, 3)
                w.text(row, 4, day.status.label, dayHeadNum)
                w.merge(row, 4, row, 5)
                w.rowHeight(row, 20.0)
                row++
            }
            row++ // luft mellan boxar
        }

        // Summa
        w.text(row, 1, "Summa veckan", totalLabel)
        w.merge(row, 1, row, 3)
        w.number(row, 4, weekHours, totalNum)
        w.number(row, 5, weekOb, totalNum)
        w.rowHeight(row, 22.0)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "Tidrapport_v${week}_$year.xlsx")
        file.outputStream().use { w.writeTo(it) }
        return file
    }

    /** Öppnar Gmail med rapporten bifogad. Faller tillbaka till delningsmeny. */
    fun shareToGmail(context: Context, file: File, week: Int, year: Int, userName: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val subject = "Tidrapport vecka $week" + if (userName.isNotBlank()) " – $userName" else ""
        val body = "Hej!\n\nBifogat är min tidrapport för vecka $week, $year.\n\n" +
            if (userName.isNotBlank()) "Vänliga hälsningar\n$userName" else "Vänliga hälsningar"

        val base = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val gmail = Intent(base).setPackage("com.google.android.gm")
        try {
            context.startActivity(gmail)
        } catch (e: ActivityNotFoundException) {
            val chooser = Intent.createChooser(base, "Skicka rapport")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
