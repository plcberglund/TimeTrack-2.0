"use strict";
(function () {
    // ---- Konstanter ----
    const SV = "sv-SE";
    const STORAGE_KEY = "timetrack.v1";
    const STATUSES = ["LEDIG", "SJUK", "SEMESTER"];
    const STATUS_LABEL = { LEDIG: "Ledig", SJUK: "Sjuk", SEMESTER: "Semester" };

    // ---- Datumhjälp (epochDay = dagar sedan 1970-01-01, UTC-baserat) ----
    function epochFromYMD(y, m, d) { return Math.round(Date.UTC(y, m - 1, d) / 86400000); }
    function dateOfEpoch(e) { return new Date(e * 86400000); }
    function todayEpoch() { const n = new Date(); return epochFromYMD(n.getFullYear(), n.getMonth() + 1, n.getDate()); }
    function isoDow(e) { return ((dateOfEpoch(e).getUTCDay() + 6) % 7) + 1; }
    function mondayEpochOf(e) { return e - (isoDow(e) - 1); }
    function weekDays(monday) { const a = []; for (let i = 0; i < 7; i++) a.push(monday + i); return a; }
    function isoWeekInfo(e) {
        const dt = dateOfEpoch(e);
        const t = new Date(Date.UTC(dt.getUTCFullYear(), dt.getUTCMonth(), dt.getUTCDate()));
        const dayNr = (t.getUTCDay() + 6) % 7;
        t.setUTCDate(t.getUTCDate() - dayNr + 3);
        const year = t.getUTCFullYear();
        const jan4 = new Date(Date.UTC(year, 0, 4));
        const week = 1 + Math.round((t - jan4) / (7 * 86400000));
        return { week: week, year: year };
    }

    function fmtDate(e, opts) { return new Intl.DateTimeFormat(SV, Object.assign({ timeZone: "UTC" }, opts)).format(dateOfEpoch(e)); }
    function cap(s) { return s.charAt(0).toUpperCase() + s.slice(1); }
    function dayName(e) { return cap(fmtDate(e, { weekday: "long" })); }
    function dayMonth(e) { return fmtDate(e, { day: "numeric", month: "long" }); }
    function rangeLabel(monday) { return dayMonth(monday) + " – " + dayMonth(monday + 6); }
    function monthLabel(e) { return cap(fmtDate(e, { month: "long", year: "numeric" })); }

    function formatHours(v) { return v.toLocaleString(SV, { maximumFractionDigits: 1 }); }
    function formatHoursLabel(v) { return formatHours(v) + " h"; }
    function parseHours(s) { const n = parseFloat(String(s).replace(",", ".")); return isNaN(n) || n < 0 ? 0 : n; }
    function sanitizeHour(s) {
        let c = String(s).replace(/\./g, ",").replace(/[^0-9,]/g, "");
        const i = c.indexOf(",");
        if (i === -1) return c;
        return c.slice(0, i) + "," + c.slice(i + 1).replace(/,/g, "");
    }
    function esc(s) {
        return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    // ---- State ----
    let state = { shifts: [], marks: {}, suggestions: { company: [], workplace: [] }, userName: "" };
    let ui = { monday: mondayEpochOf(todayEpoch()), viewMode: "week" };

    function load() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (raw) {
                const p = JSON.parse(raw);
                state.shifts = p.shifts || [];
                state.marks = p.marks || {};
                state.suggestions = p.suggestions || { company: [], workplace: [] };
                state.suggestions.company = state.suggestions.company || [];
                state.suggestions.workplace = state.suggestions.workplace || [];
                state.userName = p.userName || "";
            }
        } catch (e) { /* ignore */ }
    }
    function save() { try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch (e) {} }

    function shiftsForDay(e) { return state.shifts.filter(function (s) { return s.date === e; }).sort(function (a, b) { return a.createdAt - b.createdAt; }); }
    function sortedSuggestions(field) {
        return state.suggestions[field].slice().sort(function (a, b) { return b.count - a.count || b.lastUsed - a.lastUsed; }).map(function (s) { return s.value; });
    }
    function rememberSuggestion(field, raw) {
        const value = (raw || "").trim(); if (!value) return;
        const list = state.suggestions[field];
        const ex = list.find(function (s) { return s.value === value; });
        if (ex) { ex.count++; ex.lastUsed = Date.now(); }
        else list.push({ value: value, count: 1, lastUsed: Date.now() });
    }
    function deleteSuggestion(field, value) {
        state.suggestions[field] = state.suggestions[field].filter(function (s) { return s.value !== value; });
        save();
    }

    function saveShift(id, dateEpoch, company, workplace, note, hours, ob) {
        delete state.marks[dateEpoch];
        if (id) {
            const sh = state.shifts.find(function (s) { return s.id === id; });
            if (sh) { sh.company = company.trim(); sh.workplace = workplace.trim(); sh.note = note.trim(); sh.hours = hours; sh.obHours = ob; }
        } else {
            state.shifts.push({
                id: "s" + Date.now() + Math.floor(Math.random() * 1000),
                date: dateEpoch, company: company.trim(), workplace: workplace.trim(),
                note: note.trim(), hours: hours, obHours: ob, createdAt: Date.now(),
            });
        }
        rememberSuggestion("company", company);
        rememberSuggestion("workplace", workplace);
        save();
    }
    function deleteShift(id) { state.shifts = state.shifts.filter(function (s) { return s.id !== id; }); save(); }
    function toggleStatus(e, status) {
        if (state.marks[e] === status) delete state.marks[e]; else state.marks[e] = status;
        save();
    }

    // ---- Ikoner ----
    const IC = {
        gear: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
        left: '<svg viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"/></svg>',
        right: '<svg viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"/></svg>',
        plus: '<svg viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>',
        send: '<svg viewBox="0 0 24 24"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>',
        trash: '<svg viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>',
    };

    // ---- Render ----
    function render() {
        const app = document.getElementById("app");
        const week = ui.viewMode === "week";
        app.innerHTML =
            headerHtml() + tabsHtml() +
            '<div class="scroll">' + (week ? weekScrollHtml() : monthScrollHtml()) + "</div>" +
            (week ? sendbarHtml() : "");
    }

    function headerHtml() {
        const nm = state.userName ? esc(state.userName) : "Ditt namn";
        return '<div class="header"><div class="titles">' +
            '<div class="brand">T I M E T R A C K</div>' +
            '<div class="name' + (state.userName ? "" : " empty") + '">' + nm + "</div></div>" +
            '<button class="icon-btn" data-action="settings" aria-label="Inställningar">' + IC.gear + "</button></div>";
    }
    function tabsHtml() {
        const w = ui.viewMode === "week";
        return '<div class="tabs">' +
            '<button class="tab' + (w ? " active" : "") + '" data-action="tab" data-mode="week">Vecka</button>' +
            '<button class="tab' + (!w ? " active" : "") + '" data-action="tab" data-mode="month">Månad</button></div>';
    }

    function weekScrollHtml() {
        const monday = ui.monday;
        const info = isoWeekInfo(monday);
        const nextInfo = isoWeekInfo(monday + 7);
        const nextShifts = state.shifts.filter(function (s) { return s.date >= monday + 7 && s.date <= monday + 13; });
        const nextHasData = nextShifts.length > 0 || weekDays(monday + 7).some(function (e) { return state.marks[e]; });
        const nextHours = nextShifts.reduce(function (a, s) { return a + s.hours; }, 0);
        const nextOb = nextShifts.reduce(function (a, s) { return a + s.obHours; }, 0);
        let weekHours = 0;
        weekDays(monday).forEach(function (e) { shiftsForDay(e).forEach(function (s) { weekHours += s.hours; }); });

        let nav =
            '<div class="weeknav">' +
            '<button class="nav-square" data-action="prev" aria-label="Föregående vecka">' + IC.left + "</button>" +
            '<div class="center"><div class="wk">V. ' + info.week + '</div><div class="range">' + esc(rangeLabel(monday)) + "</div></div>" +
            '<button class="nav-square" data-action="next" aria-label="Nästa vecka">' + IC.right + "</button></div>";

        let nextSub = "–";
        if (nextHasData) { nextSub = formatHoursLabel(nextHours) + (nextOb > 0 ? " · OB " + formatHours(nextOb) : ""); }
        let chips =
            '<div class="chips">' +
            '<div class="chip current"><div class="wk">V. ' + info.week + '</div><div class="sub">' + formatHoursLabel(weekHours) + "</div></div>" +
            '<div class="chip next" data-action="pick-next"><div class="wk">V. ' + nextInfo.week + '</div><div class="sub">' + nextSub + "</div></div></div>";

        let days = "";
        weekDays(monday).forEach(function (e) { days += dayCardHtml(e); });
        return nav + chips + days;
    }

    function dayCardHtml(e) {
        const sh = shiftsForDay(e);
        const status = sh.length ? null : (state.marks[e] || null);
        const dayHours = sh.reduce(function (a, s) { return a + s.hours; }, 0);
        const isToday = e === todayEpoch();

        let html = '<div class="day-card' + (isToday ? " today" : "") + '">';
        html += '<div class="day-head"><span class="dname">' + dayName(e) + "</span>";
        if (isToday) html += '<span class="badge">IDAG</span>';
        html += '<span class="spacer"></span><span class="ddate">' + esc(dayMonth(e)) + "</span>";
        html += '<span class="dhours">' + formatHoursLabel(dayHours) + "</span></div>";

        sh.forEach(function (s) {
            html += '<div class="shift-row" data-action="edit" data-id="' + s.id + '"><div class="info">' +
                '<div class="company">' + esc(s.company || "(utan företag)") + "</div>" +
                (s.workplace ? '<div class="wp">' + esc(s.workplace) + "</div>" : "") +
                (s.note ? '<div class="note">' + esc(s.note) + "</div>" : "") +
                '</div><div class="right"><div class="rh">' + formatHoursLabel(s.hours) + "</div>" +
                (s.obHours > 0 ? '<div class="rob">OB ' + formatHours(s.obHours) + "</div>" : "") +
                "</div></div>";
        });

        html += '<button class="add-pass" data-action="add" data-date="' + e + '">' + IC.plus + "<span>Lägg till pass</span></button>";

        if (!sh.length) {
            html += '<div class="status-row">';
            STATUSES.forEach(function (st) {
                const sel = status === st;
                html += '<button class="status-btn' + (sel ? " sel" : "") + '" data-action="status" data-date="' + e + '" data-status="' + st + '">' + STATUS_LABEL[st] + "</button>";
            });
            html += "</div>";
        }
        html += "</div>";
        return html;
    }

    function sendbarHtml() {
        let total = 0;
        weekDays(ui.monday).forEach(function (e) { shiftsForDay(e).forEach(function (s) { total += s.hours; }); });
        return '<div class="sendbar-wrap"><div class="sendbar" data-action="send">' + IC.send +
            '<span class="label">Skicka rapport</span><span class="spacer"></span>' +
            '<span class="total">' + formatHoursLabel(total) + "</span></div></div>";
    }

    function monthScrollHtml() {
        const year = new Date().getFullYear();
        const ofYear = state.shifts.filter(function (s) { return dateOfEpoch(s.date).getUTCFullYear() === year; });
        const byMonth = {};
        ofYear.forEach(function (s) {
            const m = dateOfEpoch(s.date).getUTCMonth();
            if (!byMonth[m]) byMonth[m] = { hours: 0, ob: 0 };
            byMonth[m].hours += s.hours; byMonth[m].ob += s.obHours;
        });
        let totalH = 0, totalOb = 0;
        ofYear.forEach(function (s) { totalH += s.hours; totalOb += s.obHours; });

        let head = '<div class="month-head"><div><div class="yr">' + year + '</div>' +
            '<div class="obtot">OB totalt: ' + formatHoursLabel(totalOb) + "</div></div>" +
            '<span class="spacer"></span><div class="sofar">' + formatHoursLabel(totalH) + " hittills</div></div>";

        const months = Object.keys(byMonth).map(Number).sort(function (a, b) { return b - a; });
        let cards = "";
        months.forEach(function (m) {
            const first = epochFromYMD(year, m + 1, 1);
            const d = byMonth[m];
            cards += '<div class="month-card"><div class="ml">' + monthLabel(first) + "</div><div>" +
                '<div class="mh">' + formatHoursLabel(d.hours) + "</div>" +
                (d.ob > 0 ? '<div class="mob">OB ' + formatHoursLabel(d.ob) + "</div>" : "") + "</div></div>";
        });
        if (!months.length) cards = '<div class="empty-note">Ingen rapporterad tid ännu i år.</div>';
        return head + cards;
    }

    // ---- Klickhantering ----
    document.getElementById("app").addEventListener("click", function (ev) {
        const el = ev.target.closest("[data-action]");
        if (!el) return;
        const a = el.getAttribute("data-action");
        if (a === "settings") openSettings();
        else if (a === "tab") { ui.viewMode = el.getAttribute("data-mode"); render(); }
        else if (a === "prev") { ui.monday = ui.monday - 7; render(); }
        else if (a === "next" || a === "pick-next") { ui.monday = ui.monday + 7; render(); }
        else if (a === "add") openSheet(parseInt(el.getAttribute("data-date"), 10), null);
        else if (a === "edit") {
            const sh = state.shifts.find(function (s) { return s.id === el.getAttribute("data-id"); });
            if (sh) openSheet(sh.date, sh);
        } else if (a === "status") { toggleStatus(parseInt(el.getAttribute("data-date"), 10), el.getAttribute("data-status")); render(); }
        else if (a === "send") sendReport();
    });

    // ---- Bottom sheet (nytt/redigera pass) ----
    function openSheet(dateEpoch, shift) {
        const editing = !!shift;
        const root = document.getElementById("sheet-root");
        const compSug = sortedSuggestions("company");
        const wpSug = sortedSuggestions("workplace");

        root.innerHTML =
            '<div class="overlay" id="ov"><div class="sheet" id="sheet">' +
            "<h2>" + (editing ? "Redigera pass" : "Nytt arbetspass") + "</h2>" +
            '<div class="subtitle">' + dayName(dateEpoch) + " " + esc(dayMonth(dateEpoch)) + "</div>" +
            '<div class="label first">Företag</div>' +
            '<input class="field" id="f-company" placeholder="Skriv företag" value="' + esc(shift ? shift.company : "") + '" />' +
            '<div class="quickchips" id="chips-company"></div>' +
            (compSug.length ? '<div class="hint">Tips: håll inne en snabbknapp för att ta bort den.</div>' : "") +
            '<div class="label">Arbetsplats / plats</div>' +
            '<input class="field" id="f-workplace" placeholder="Skriv arbetsplats" value="' + esc(shift ? shift.workplace : "") + '" />' +
            '<div class="quickchips" id="chips-workplace"></div>' +
            '<div class="label">Anteckning</div>' +
            '<textarea class="field" id="f-note" rows="2" placeholder="Valfri anteckning">' + esc(shift ? shift.note : "") + "</textarea>" +
            '<div class="label">Antal timmar</div>' +
            '<input class="field" id="f-hours" inputmode="decimal" placeholder="0" value="' + (shift && shift.hours ? esc(formatHours(shift.hours)) : "") + '" />' +
            '<div class="label">OB-timmar</div>' +
            '<input class="field" id="f-ob" inputmode="decimal" placeholder="0" value="' + (shift && shift.obHours ? esc(formatHours(shift.obHours)) : "") + '" />' +
            '<div class="hint">Räknas separat – läggs inte ovanpå de vanliga timmarna.</div>' +
            (editing ? '<div class="delete-row" id="del">' + IC.trash + "<span>Ta bort pass</span></div>" : "") +
            '<button class="primary-btn" id="save" disabled>Spara</button>' +
            "</div></div>";

        requestAnimationFrame(function () { document.getElementById("ov").classList.add("show"); });

        const comp = document.getElementById("f-company");
        const wp = document.getElementById("f-workplace");
        const note = document.getElementById("f-note");
        const hours = document.getElementById("f-hours");
        const ob = document.getElementById("f-ob");
        const saveBtn = document.getElementById("save");

        function refreshSave() {
            const ok = comp.value.trim() !== "" && (parseHours(hours.value) > 0 || parseHours(ob.value) > 0);
            saveBtn.disabled = !ok;
        }
        [comp, wp, note].forEach(function (i) { i.addEventListener("input", refreshSave); });
        [hours, ob].forEach(function (i) { i.addEventListener("input", function () { const p = i.selectionStart; i.value = sanitizeHour(i.value); refreshSave(); }); });
        refreshSave();

        renderChips("chips-company", compSug, comp, "company");
        renderChips("chips-workplace", wpSug, wp, "workplace");

        function close() {
            const ov = document.getElementById("ov");
            ov.classList.remove("show");
            setTimeout(function () { root.innerHTML = ""; }, 200);
        }
        document.getElementById("ov").addEventListener("click", function (e) { if (e.target.id === "ov") close(); });
        if (editing) document.getElementById("del").addEventListener("click", function () {
            confirmDialog("Ta bort pass", "Vill du ta bort det här passet?", "Ta bort", true, function () {
                deleteShift(shift.id); close(); render();
            });
        });
        saveBtn.addEventListener("click", function () {
            if (saveBtn.disabled) return;
            saveShift(editing ? shift.id : null, dateEpoch, comp.value, wp.value, note.value, parseHours(hours.value), parseHours(ob.value));
            close(); render();
        });
    }

    function renderChips(containerId, options, input, field) {
        const c = document.getElementById(containerId);
        if (!options.length) { c.style.display = "none"; return; }
        c.innerHTML = options.map(function (o) { return '<div class="qchip" data-v="' + esc(o) + '">' + esc(o) + "</div>"; }).join("");
        Array.prototype.forEach.call(c.children, function (chip) {
            const value = chip.getAttribute("data-v");
            let timer = null, longPressed = false;
            function startPress() { longPressed = false; timer = setTimeout(function () { longPressed = true; askDeleteChip(field, value, input, containerId, options); }, 500); }
            function cancelPress() { if (timer) { clearTimeout(timer); timer = null; } }
            chip.addEventListener("pointerdown", startPress);
            chip.addEventListener("pointerup", cancelPress);
            chip.addEventListener("pointermove", cancelPress);
            chip.addEventListener("pointercancel", cancelPress);
            chip.addEventListener("click", function () { if (longPressed) { longPressed = false; return; } input.value = value; input.dispatchEvent(new Event("input")); });
            chip.addEventListener("contextmenu", function (e) { e.preventDefault(); askDeleteChip(field, value, input, containerId, options); });
        });
    }
    function askDeleteChip(field, value, input, containerId) {
        confirmDialog("Ta bort snabbknapp", 'Vill du ta bort "' + esc(value) + '"?', "Ta bort", true, function () {
            deleteSuggestion(field, value);
            renderChips(containerId, sortedSuggestions(field), input, field);
        });
    }

    // ---- Dialoger ----
    function openSettings() {
        const root = document.getElementById("sheet-root");
        root.innerHTML =
            '<div class="overlay center" id="ov2"><div class="dialog">' +
            "<h3>Inställningar</h3>" +
            '<div class="label" style="color:var(--text-secondary);font-size:14px;margin:0 0 8px;">Ditt namn</div>' +
            '<input class="field" id="s-name" placeholder="Förnamn Efternamn" value="' + esc(state.userName) + '" />' +
            '<p>Namnet visas högst upp och i Excel-rapporten du skickar.</p>' +
            '<div class="dialog-actions"><button class="cancel" id="s-cancel">Avbryt</button><button class="confirm" id="s-save">Spara</button></div>' +
            "</div></div>";
        requestAnimationFrame(function () { document.getElementById("ov2").classList.add("show"); });
        function close() { const o = document.getElementById("ov2"); o.classList.remove("show"); setTimeout(function () { root.innerHTML = ""; }, 180); }
        document.getElementById("ov2").addEventListener("click", function (e) { if (e.target.id === "ov2") close(); });
        document.getElementById("s-cancel").addEventListener("click", close);
        document.getElementById("s-save").addEventListener("click", function () {
            state.userName = document.getElementById("s-name").value.trim(); save(); close(); render();
        });
    }

    function confirmDialog(title, message, confirmLabel, danger, onConfirm) {
        const holder = document.createElement("div");
        holder.innerHTML =
            '<div class="overlay center" id="cf"><div class="dialog">' +
            "<h3>" + esc(title) + "</h3><p style=\"color:var(--text-secondary);margin-top:4px;\">" + message + "</p>" +
            '<div class="dialog-actions"><button class="cancel" id="cf-cancel">Avbryt</button>' +
            '<button class="confirm' + (danger ? " danger" : "") + '" id="cf-ok">' + esc(confirmLabel) + "</button></div></div></div>";
        document.body.appendChild(holder);
        requestAnimationFrame(function () { holder.querySelector("#cf").classList.add("show"); });
        function close() { const o = holder.querySelector("#cf"); o.classList.remove("show"); setTimeout(function () { holder.remove(); }, 180); }
        holder.querySelector("#cf").addEventListener("click", function (e) { if (e.target.id === "cf") close(); });
        holder.querySelector("#cf-cancel").addEventListener("click", close);
        holder.querySelector("#cf-ok").addEventListener("click", function () { close(); onConfirm(); });
    }

    let toastTimer = null;
    function toast(msg) {
        let t = document.getElementById("toast");
        if (!t) { t = document.createElement("div"); t.id = "toast"; t.className = "toast"; document.body.appendChild(t); }
        t.textContent = msg;
        requestAnimationFrame(function () { t.classList.add("show"); });
        if (toastTimer) clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { t.classList.remove("show"); }, 2600);
    }

    // ---- Skicka rapport ----
    async function sendReport() {
        const monday = ui.monday;
        const info = isoWeekInfo(monday);
        const days = weekDays(monday).map(function (e) {
            const sh = shiftsForDay(e);
            const status = sh.length ? null : (state.marks[e] ? STATUS_LABEL[state.marks[e]] : null);
            return {
                heading: dayName(e) + "  " + dayMonth(e),
                status: status,
                shifts: sh.map(function (s) { return { company: s.company, workplace: s.workplace, note: s.note, hours: s.hours, obHours: s.obHours }; }),
            };
        }).filter(function (d) { return d.shifts.length || d.status; });

        if (!days.length) { toast("Inget att skicka för vecka " + info.week + " ännu"); return; }

        const bytes = window.TimeTrackXlsx.build({ userName: state.userName, week: info.week, year: info.year, rangeLabel: rangeLabel(monday), days: days });
        const fname = "Tidrapport_v" + info.week + "_" + info.year + ".xlsx";
        const blob = new Blob([bytes], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
        const subject = "Tidrapport vecka " + info.week + (state.userName ? " – " + state.userName : "");
        const text = "Hej!\n\nBifogat är min tidrapport för vecka " + info.week + ", " + info.year + ".\n\n" + (state.userName ? "Vänliga hälsningar\n" + state.userName : "Vänliga hälsningar");

        try {
            const file = new File([blob], fname, { type: blob.type });
            if (navigator.canShare && navigator.canShare({ files: [file] })) {
                await navigator.share({ files: [file], title: subject, text: text });
                return;
            }
        } catch (e) { if (e && e.name === "AbortError") return; }

        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url; a.download = fname; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
        toast("Excel-filen laddades ner");
    }

    // ---- Start ----
    load();
    render();
})();
