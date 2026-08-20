/* Lättviktig XLSX-generator (ingen tredjepart). Speglar Kotlin-appens
   XlsxWriter + ReportExporter: dag-boxar, rena orangea rader, summarad. */
(function (global) {
    "use strict";

    // ---- CRC32 ----
    const crcTable = (function () {
        const t = new Uint32Array(256);
        for (let n = 0; n < 256; n++) {
            let c = n;
            for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
            t[n] = c >>> 0;
        }
        return t;
    })();
    function crc32(bytes) {
        let c = 0xFFFFFFFF;
        for (let i = 0; i < bytes.length; i++) c = crcTable[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
        return (c ^ 0xFFFFFFFF) >>> 0;
    }

    // ---- STORE zip ----
    function u16(n) { return [n & 0xFF, (n >>> 8) & 0xFF]; }
    function u32(n) { return [n & 0xFF, (n >>> 8) & 0xFF, (n >>> 16) & 0xFF, (n >>> 24) & 0xFF]; }

    function zipStore(files) {
        const enc = new TextEncoder();
        const locals = [];
        const centrals = [];
        let offset = 0;
        for (const f of files) {
            const nameBytes = enc.encode(f.name);
            const data = f.data;
            const crc = crc32(data);
            const local = [].concat(
                u32(0x04034b50), u16(20), u16(0), u16(0), u16(0), u16(0),
                u32(crc), u32(data.length), u32(data.length),
                u16(nameBytes.length), u16(0)
            );
            const localHeader = new Uint8Array(local);
            locals.push(localHeader, nameBytes, data);
            const localSize = localHeader.length + nameBytes.length + data.length;

            const central = [].concat(
                u32(0x02014b50), u16(20), u16(20), u16(0), u16(0), u16(0), u16(0),
                u32(crc), u32(data.length), u32(data.length),
                u16(nameBytes.length), u16(0), u16(0), u16(0), u16(0),
                u32(0), u32(offset)
            );
            centrals.push(new Uint8Array(central), nameBytes);
            offset += localSize;
        }
        const cdStart = offset;
        let cdSize = 0;
        for (const c of centrals) cdSize += c.length;
        const end = new Uint8Array([].concat(
            u32(0x06054b50), u16(0), u16(0),
            u16(files.length), u16(files.length),
            u32(cdSize), u32(cdStart), u16(0)
        ));

        const parts = locals.concat(centrals, [end]);
        let total = 0;
        for (const p of parts) total += p.length;
        const out = new Uint8Array(total);
        let pos = 0;
        for (const p of parts) { out.set(p, pos); pos += p.length; }
        return out;
    }

    // ---- XLSX writer ----
    function esc(s) {
        return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");
    }
    function colLetter(col) {
        let c = col, s = "";
        while (c > 0) { const r = (c - 1) % 26; s = String.fromCharCode(65 + r) + s; c = ((c - 1) / 26) | 0; }
        return s;
    }
    function trimNum(v) { return Number.isInteger(v) ? String(v) : String(v); }

    function Writer(sheetName) {
        this.sheetName = sheetName || "Rapport";
        this.rows = {};        // rowIdx -> { colIdx -> {isNum, value, styleId} }
        this.merges = [];
        this.colWidths = {};
        this.rowHeights = {};
        this.styles = [];      // list of JSON keys
        this.styleDefs = [];
        this.maxCol = 1;
    }
    Writer.prototype.style = function (s) {
        const key = JSON.stringify(s);
        let i = this.styles.indexOf(key);
        if (i >= 0) return i;
        this.styles.push(key); this.styleDefs.push(s);
        return this.styles.length - 1;
    };
    Writer.prototype.put = function (r, c, cell) {
        if (!this.rows[r]) this.rows[r] = {};
        this.rows[r][c] = cell;
        if (c > this.maxCol) this.maxCol = c;
    };
    Writer.prototype.text = function (r, c, v, s) { this.put(r, c, { isNum: false, value: v, styleId: s == null ? -1 : s }); };
    Writer.prototype.number = function (r, c, v, s) { this.put(r, c, { isNum: true, value: trimNum(v), styleId: s == null ? -1 : s }); };
    Writer.prototype.merge = function (r1, c1, r2, c2) { this.merges.push(colLetter(c1) + r1 + ":" + colLetter(c2) + r2); if (c2 > this.maxCol) this.maxCol = c2; };
    Writer.prototype.colWidth = function (c, w) { this.colWidths[c] = w; };
    Writer.prototype.rowHeight = function (r, h) { this.rowHeights[r] = h; };

    Writer.prototype.sheetXml = function () {
        let x = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
        x += '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">';
        const cols = Object.keys(this.colWidths).map(Number).sort((a, b) => a - b);
        if (cols.length) {
            x += "<cols>";
            for (const c of cols) x += '<col min="' + c + '" max="' + c + '" width="' + this.colWidths[c] + '" customWidth="1"/>';
            x += "</cols>";
        }
        x += "<sheetData>";
        const rowIdxs = Object.keys(this.rows).map(Number).sort((a, b) => a - b);
        for (const r of rowIdxs) {
            const ht = this.rowHeights[r];
            x += ht ? '<row r="' + r + '" ht="' + ht + '" customHeight="1">' : '<row r="' + r + '">';
            const cells = this.rows[r];
            const colIdxs = Object.keys(cells).map(Number).sort((a, b) => a - b);
            for (const c of colIdxs) {
                const cell = cells[c];
                const ref = colLetter(c) + r;
                const s = cell.styleId >= 0 ? ' s="' + (cell.styleId + 1) + '"' : "";
                if (cell.isNum) x += '<c r="' + ref + '"' + s + '><v>' + cell.value + '</v></c>';
                else x += '<c r="' + ref + '"' + s + ' t="inlineStr"><is><t xml:space="preserve">' + esc(cell.value) + '</t></is></c>';
            }
            x += "</row>";
        }
        x += "</sheetData>";
        if (this.merges.length) {
            x += '<mergeCells count="' + this.merges.length + '">';
            for (const m of this.merges) x += '<mergeCell ref="' + m + '"/>';
            x += "</mergeCells>";
        }
        x += "</worksheet>";
        return x;
    };

    Writer.prototype.stylesXml = function () {
        const defs = this.styleDefs;
        let x = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
        x += '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">';
        // fonts
        x += '<fonts count="' + (defs.length + 1) + '">';
        x += '<font><sz val="11"/><color rgb="FF222222"/><name val="Calibri"/></font>';
        for (const s of defs) {
            x += "<font>";
            if (s.bold) x += "<b/>";
            x += '<sz val="' + (s.fontSize || 11) + '"/>';
            x += '<color rgb="' + (s.fontColor || "FF222222") + '"/>';
            x += '<name val="Calibri"/></font>';
        }
        x += "</fonts>";
        // fills
        x += '<fills count="' + (defs.length + 2) + '">';
        x += '<fill><patternFill patternType="none"/></fill>';
        x += '<fill><patternFill patternType="gray125"/></fill>';
        for (const s of defs) {
            if (s.fill) x += '<fill><patternFill patternType="solid"><fgColor rgb="FF' + s.fill + '"/><bgColor indexed="64"/></patternFill></fill>';
            else x += '<fill><patternFill patternType="none"/></fill>';
        }
        x += "</fills>";
        // borders
        x += '<borders count="' + (defs.length + 1) + '">';
        x += "<border><left/><right/><top/><bottom/><diagonal/></border>";
        for (const s of defs) {
            if (s.border) {
                const col = '<color rgb="' + (s.borderColor || "FFDDDDDD") + '"/>';
                x += "<border>" +
                    '<left style="thin">' + col + "</left>" +
                    '<right style="thin">' + col + "</right>" +
                    '<top style="thin">' + col + "</top>" +
                    '<bottom style="thin">' + col + "</bottom>" +
                    "<diagonal/></border>";
            } else x += "<border><left/><right/><top/><bottom/><diagonal/></border>";
        }
        x += "</borders>";
        x += '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>';
        x += '<cellXfs count="' + (defs.length + 1) + '">';
        x += '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>';
        defs.forEach(function (s, i) {
            x += '<xf numFmtId="0" fontId="' + (i + 1) + '" fillId="' + (i + 2) + '" borderId="' + (i + 1) + '" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">';
            const h = s.hAlign ? ' horizontal="' + s.hAlign + '"' : "";
            const wrap = s.wrap ? ' wrapText="1"' : "";
            x += "<alignment" + h + ' vertical="' + (s.vAlign || "center") + '"' + wrap + "/></xf>";
        });
        x += "</cellXfs>";
        x += '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>';
        x += "</styleSheet>";
        return x;
    };

    Writer.prototype.toBytes = function () {
        const enc = new TextEncoder();
        const contentTypes = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
            '<Default Extension="xml" ContentType="application/xml"/>' +
            '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
            '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' +
            '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>';
        const rootRels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>';
        const workbook = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
            '<sheets><sheet name="' + esc(this.sheetName) + '" sheetId="1" r:id="rId1"/></sheets></workbook>';
        const wbRels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>' +
            '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>';
        const files = [
            { name: "[Content_Types].xml", data: enc.encode(contentTypes) },
            { name: "_rels/.rels", data: enc.encode(rootRels) },
            { name: "xl/workbook.xml", data: enc.encode(workbook) },
            { name: "xl/_rels/workbook.xml.rels", data: enc.encode(wbRels) },
            { name: "xl/styles.xml", data: enc.encode(this.stylesXml()) },
            { name: "xl/worksheets/sheet1.xml", data: enc.encode(this.sheetXml()) },
        ];
        return zipStore(files);
    };

    // ---- Report builder (matchar ReportExporter) ----
    const ORANGE = "F26A0E", ORANGE_SOFT = "FCEAD9", GRAY_HEAD = "EFEFEF", GRAY_SOFT = "F7F7F7";

    function buildReport(report) {
        // report: { userName, week, year, rangeLabel, days:[{heading,status,shifts:[{company,workplace,note,hours,obHours}]}] }
        const w = new Writer("Vecka " + report.week);
        w.colWidth(1, 24); w.colWidth(2, 24); w.colWidth(3, 30); w.colWidth(4, 11); w.colWidth(5, 13);

        const title = w.style({ bold: true, fontSize: 22, fontColor: "FF" + ORANGE, hAlign: "left" });
        const name = w.style({ bold: true, fontSize: 12, fontColor: "FF333333", hAlign: "left" });
        const sub = w.style({ fontSize: 11, fontColor: "FF888888", hAlign: "left" });
        const dayHead = w.style({ bold: true, fontSize: 12, fontColor: "FFFFFFFF", fill: ORANGE, border: true, borderColor: "FF" + ORANGE, hAlign: "left" });
        const colHead = w.style({ bold: true, fontSize: 10, fontColor: "FF555555", fill: GRAY_HEAD, border: true, hAlign: "left" });
        const colHeadNum = w.style({ bold: true, fontSize: 10, fontColor: "FF555555", fill: GRAY_HEAD, border: true, hAlign: "right" });
        const cell = w.style({ fontSize: 11, fontColor: "FF333333", border: true, hAlign: "left", wrap: true });
        const cellNum = w.style({ fontSize: 11, fontColor: "FF333333", border: true, hAlign: "right" });
        const totalLabel = w.style({ bold: true, fontSize: 13, fontColor: "FF333333", fill: ORANGE_SOFT, border: true, borderColor: "FF" + ORANGE, hAlign: "right" });
        const totalNum = w.style({ bold: true, fontSize: 13, fontColor: "FF" + ORANGE, fill: ORANGE_SOFT, border: true, borderColor: "FF" + ORANGE, hAlign: "right" });

        let row = 1;
        w.text(row, 1, "TIDRAPPORT", title); w.merge(row, 1, row, 5); w.rowHeight(row, 30); row++;
        if (report.userName) { w.text(row, 1, report.userName, name); w.merge(row, 1, row, 5); row++; }
        w.text(row, 1, "Vecka " + report.week + ", " + report.year + "  ·  " + report.rangeLabel, sub);
        w.merge(row, 1, row, 5); row++; row++;

        let weekH = 0, weekOb = 0;
        for (const day of report.days) {
            const dh = day.shifts.reduce((a, s) => a + s.hours, 0);
            const dob = day.shifts.reduce((a, s) => a + s.obHours, 0);
            weekH += dh; weekOb += dob;

            if (day.shifts.length) {
                w.text(row, 1, day.heading, dayHead); w.merge(row, 1, row, 5); w.rowHeight(row, 20); row++;
                w.text(row, 1, "Företag", colHead); w.text(row, 2, "Arbetsplats", colHead);
                w.text(row, 3, "Anteckning", colHead); w.text(row, 4, "Timmar", colHeadNum); w.text(row, 5, "OB-tim", colHeadNum); row++;
                for (const s of day.shifts) {
                    w.text(row, 1, s.company, cell); w.text(row, 2, s.workplace, cell); w.text(row, 3, s.note, cell);
                    w.number(row, 4, s.hours, cellNum); w.number(row, 5, s.obHours, cellNum); row++;
                }
            } else if (day.status) {
                w.text(row, 1, day.heading + "  ·  " + day.status, dayHead); w.merge(row, 1, row, 5); w.rowHeight(row, 20); row++;
            }
            row++;
        }
        w.text(row, 1, "Summa veckan", totalLabel); w.merge(row, 1, row, 3);
        w.number(row, 4, weekH, totalNum); w.number(row, 5, weekOb, totalNum); w.rowHeight(row, 22);

        return w.toBytes();
    }

    global.TimeTrackXlsx = { build: buildReport };
})(window);
