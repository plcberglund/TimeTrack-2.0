package com.timetrack.util

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal men formatstark XLSX-skrivare (inga tredjepartsberoenden).
 * Stödjer text/tal-celler, sammanslagna celler, kolumnbredder, radhöjder
 * samt cellstilar (typsnitt, fyllning, kant, justering).
 *
 * Rad- och kolumnindex är 1-baserade (A1 = rad 1, kol 1).
 */
class XlsxWriter(private val sheetName: String = "Rapport") {

    data class Style(
        val bold: Boolean = false,
        val fontSize: Int = 11,
        val fontColor: String = "FF222222", // ARGB
        val fill: String? = null,           // RGB eller null
        val border: Boolean = false,
        val borderColor: String = "FFDDDDDD",
        val hAlign: String? = null,         // left/center/right
        val vAlign: String = "center",
        val wrap: Boolean = false,
    )

    private class CellData(val isNumber: Boolean, val value: String, val styleId: Int)

    private val rows = sortedMapOf<Int, MutableMap<Int, CellData>>()
    private val merges = mutableListOf<String>()
    private val colWidths = mutableMapOf<Int, Double>()
    private val rowHeights = mutableMapOf<Int, Double>()
    private val styles = mutableListOf<Style>()
    private var maxCol = 1

    fun style(s: Style): Int {
        val existing = styles.indexOf(s)
        if (existing >= 0) return existing
        styles.add(s)
        return styles.size - 1
    }

    fun text(row: Int, col: Int, value: String, styleId: Int = -1) {
        put(row, col, CellData(false, value, styleId))
    }

    fun number(row: Int, col: Int, value: Double, styleId: Int = -1) {
        put(row, col, CellData(true, trimNumber(value), styleId))
    }

    fun merge(r1: Int, c1: Int, r2: Int, c2: Int) {
        merges.add("${ref(r1, c1)}:${ref(r2, c2)}")
        maxCol = maxOf(maxCol, c2)
    }

    fun colWidth(col: Int, width: Double) {
        colWidths[col] = width
    }

    fun rowHeight(row: Int, height: Double) {
        rowHeights[row] = height
    }

    private fun put(row: Int, col: Int, data: CellData) {
        rows.getOrPut(row) { mutableMapOf() }[col] = data
        maxCol = maxOf(maxCol, col)
    }

    fun writeTo(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", contentTypes())
            zip.entry("_rels/.rels", rootRels())
            zip.entry("xl/workbook.xml", workbook())
            zip.entry("xl/_rels/workbook.xml.rels", workbookRels())
            zip.entry("xl/styles.xml", stylesXml())
            zip.entry("xl/worksheets/sheet1.xml", sheetXml())
        }
    }

    // ---- XML-delar ----

    private fun sheetXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (colWidths.isNotEmpty()) {
            append("<cols>")
            for ((col, w) in colWidths.toSortedMap()) {
                append("""<col min="$col" max="$col" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        for ((rowIdx, cells) in rows) {
            val ht = rowHeights[rowIdx]
            if (ht != null) {
                append("""<row r="$rowIdx" ht="$ht" customHeight="1">""")
            } else {
                append("""<row r="$rowIdx">""")
            }
            for ((colIdx, data) in cells.toSortedMap()) {
                val s = if (data.styleId >= 0) " s=\"${data.styleId + 1}\"" else ""
                val r = ref(rowIdx, colIdx)
                if (data.isNumber) {
                    append("""<c r="$r"$s><v>${data.value}</v></c>""")
                } else {
                    append("""<c r="$r"$s t="inlineStr"><is><t xml:space="preserve">${escape(data.value)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (merges.isNotEmpty()) {
            append("""<mergeCells count="${merges.size}">""")
            for (m in merges) append("""<mergeCell ref="$m"/>""")
            append("</mergeCells>")
        }
        append("</worksheet>")
    }

    private fun stylesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Fonts: index 0 = default, sedan en per stil
        append("""<fonts count="${styles.size + 1}">""")
        append("""<font><sz val="11"/><color rgb="FF222222"/><name val="Calibri"/></font>""")
        for (s in styles) {
            append("<font>")
            if (s.bold) append("<b/>")
            append("""<sz val="${s.fontSize}"/>""")
            append("""<color rgb="${s.fontColor}"/>""")
            append("""<name val="Calibri"/>""")
            append("</font>")
        }
        append("</fonts>")

        // Fills: 0 = none, 1 = gray125 (reserverade), sedan en per stil
        append("""<fills count="${styles.size + 2}">""")
        append("""<fill><patternFill patternType="none"/></fill>""")
        append("""<fill><patternFill patternType="gray125"/></fill>""")
        for (s in styles) {
            if (s.fill != null) {
                append("""<fill><patternFill patternType="solid"><fgColor rgb="FF${s.fill}"/><bgColor indexed="64"/></patternFill></fill>""")
            } else {
                append("""<fill><patternFill patternType="none"/></fill>""")
            }
        }
        append("</fills>")

        // Borders: 0 = tom, sedan en per stil
        append("""<borders count="${styles.size + 1}">""")
        append("<border><left/><right/><top/><bottom/><diagonal/></border>")
        for (s in styles) {
            if (s.border) {
                val c = """<color rgb="${s.borderColor}"/>"""
                append("<border>")
                append("""<left style="thin">$c</left>""")
                append("""<right style="thin">$c</right>""")
                append("""<top style="thin">$c</top>""")
                append("""<bottom style="thin">$c</bottom>""")
                append("<diagonal/>")
                append("</border>")
            } else {
                append("<border><left/><right/><top/><bottom/><diagonal/></border>")
            }
        }
        append("</borders>")

        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")

        append("""<cellXfs count="${styles.size + 1}">""")
        append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""")
        styles.forEachIndexed { i, s ->
            val fontId = i + 1
            val fillId = i + 2
            val borderId = i + 1
            append("""<xf numFmtId="0" fontId="$fontId" fillId="$fillId" borderId="$borderId" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">""")
            val h = if (s.hAlign != null) """ horizontal="${s.hAlign}"""" else ""
            val wrap = if (s.wrap) """ wrapText="1"""" else ""
            append("""<alignment$h vertical="${s.vAlign}"$wrap/>""")
            append("</xf>")
        }
        append("</cellXfs>")

        append("""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""")
        append("</styleSheet>")
    }

    private fun workbook(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="${escape(sheetName)}" sheetId="1" r:id="rId1"/></sheets>""" +
            "</workbook>"

    private fun workbookRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            "</Relationships>"

    private fun contentTypes(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
            "</Types>"

    private fun rootRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    // ---- Hjälp ----

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ref(row: Int, col: Int): String = colLetter(col) + row

    private fun colLetter(col: Int): String {
        var c = col
        val sb = StringBuilder()
        while (c > 0) {
            val rem = (c - 1) % 26
            sb.insert(0, ('A' + rem))
            c = (c - 1) / 26
        }
        return sb.toString()
    }

    private fun escape(s: String): String = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(ch)
        }
    }

    private fun trimNumber(value: Double): String {
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        return value.toString()
    }
}
