import type { SuiRenderer } from "@mindconnect-ai/mc-semantic-ui-core";

// The helpers below are inlined (not imported from the core bundle) so the
// compiled extension.js has NO runtime import of /sui/renderer.js. That keeps
// the bundle portable: it works from a CDN or under a path prefix, where an
// absolute /sui/ import would resolve against the wrong origin. The `import
// type` above is erased at compile time. Same trick as the chart extension.
const HTML_ESCAPE: Record<string, string> =
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
function esc(value: unknown): string {
    if (value == null) return "";
    return String(value).replace(/[&<>"']/g, ch => HTML_ESCAPE[ch]!);
}

/** A trigger as the core defines it; only what the calendar needs to carry. */
interface Trigger {
    url?: string;
    method?: string;
    behavior?: string;
    payload?: string;
    handler?: string;
    [key: string]: unknown;
}

/** The node-level events every UiNode may carry — mirrors renderers/util.ts `evt()`. */
interface NodeEvents {
    onClick?: Trigger; onDblClick?: Trigger; onHover?: Trigger;
    onLeave?: Trigger; onChange?: Trigger; onInput?: Trigger;
}

interface NodeBase extends NodeEvents {
    id: string;
    title?: string;
    cssClass?: string;
    display?: "HIDDEN" | "BLANK" | string;
}

export type CalendarView = "DAY" | "WEEK" | "MONTH";

/** The words the calendar shows. English when absent — mirrors UiCalendar.Labels. */
export interface CalendarLabels {
    /** Seven short weekday names, Monday first. */
    weekdays?: string[];
    /** Seven full weekday names, Monday first. */
    weekdaysLong?: string[];
    /** Twelve month names, January first. */
    months?: string[];
    /** Twelve short month names, January first. */
    monthsShort?: string[];
    today?: string;
    day?: string;
    week?: string;
    month?: string;
    allDay?: string;
    /** With `{n}` for the count, e.g. `+{n} more`. */
    more?: string;
    previous?: string;
    next?: string;
}

/** Wire shape of `calendar` — mirrors UiCalendar.java. */
export interface UiCalendarWire extends NodeBase {
    type: "calendar";
    view?: CalendarView;
    /** The day shown, or a day of the week or month shown: `yyyy-MM-dd`. */
    date?: string;
    /** Today, `yyyy-MM-dd` — the server's, so both painters agree. */
    today?: string;
    /** A day to highlight, `yyyy-MM-dd`. */
    selectedDate?: string;
    weekStart?: "MONDAY" | "SUNDAY";
    /** First hour shown in the day and week views (0–23). Default 0. */
    startHour?: number;
    /** Hour the day and week views end at (1–24). Default 24. */
    endHour?: number;
    /** Chips a month cell shows before folding the rest into "+n more". Default 3. */
    maxEventsPerDay?: number;
    labels?: CalendarLabels;
    events?: UiCalendarEventWire[];
    /** Previous / next / today and the view switch: `{date}` and `{view}` in its URL. */
    onNavigate?: Trigger;
    /** A day or an hour picked: `{date}` and `{hour}` in its URL. */
    onSelect?: Trigger;
}

/** Wire shape of `calendar-event` — mirrors UiCalendarEvent.java. */
export interface UiCalendarEventWire extends NodeBase {
    type: "calendar-event";
    /** `yyyy-MM-dd` for an all-day event, `yyyy-MM-ddTHH:mm` for a timed one. */
    start?: string;
    /** Same form as `start`. Absent: one hour, or the same day. */
    end?: string;
    /** Forces all-day; absent, a `start` without a time means all-day. */
    allDay?: boolean;
    /** Accent colour (any CSS colour). */
    color?: string;
}

/** What the calendar needs of an event bus: `SuiEventBus` fits, so does a stand-in. */
export interface CalendarBus {
    dispatch(trigger: Trigger, sourceElement?: HTMLElement): Promise<void>;
}

// ── Shared helpers (twins of the core's) ────────────────────────────────────

function cls(base: string, node: NodeBase): string {
    const marker = node.display === "HIDDEN" ? "sui-hidden"
        : node.display === "BLANK" ? "sui-blank" : null;
    const own = (node.cssClass ?? "")
        .split(/\s+/)
        .filter(token => token && token !== "sui-hidden" && token !== "sui-blank")
        .join(" ");
    const parts = [base];
    if (own) parts.push(esc(own));
    if (marker) parts.push(marker);
    return parts.join(" ");
}

function encodeTrigger(trigger: Trigger): string {
    return JSON.stringify(trigger).replace(/'/g, "&#39;");
}

function evt(node: NodeEvents): string {
    const all: Array<[string, Trigger | undefined]> = [
        ["click", node.onClick], ["dblclick", node.onDblClick],
        ["hover", node.onHover], ["leave", node.onLeave],
        ["change", node.onChange], ["input", node.onInput],
    ];
    return all.filter(([, t]) => !!t)
        .map(([name, t]) => ` data-sui-on-${name}='${encodeTrigger(t!)}'`).join("");
}

// ── Dates — plain civil dates, no time zone anywhere ────────────────────────
// Everything here is mirrored in CalendarPainter.java on java.time.LocalDate.
// Arithmetic goes through Date.UTC so daylight saving never shifts a day.

/** A civil date: year, month 1–12, day 1–31. */
interface Ymd { y: number; m: number; d: number; }

export function parseDate(iso: string): Ymd {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
    if (!m) throw new Error(`sui-calendar: not a date: ${iso}`);
    return { y: Number(m[1]), m: Number(m[2]), d: Number(m[3]) };
}

/** Minutes past midnight from a `yyyy-MM-ddTHH:mm` value, or null when it carries no time. */
function parseMinutes(iso: string): number | null {
    const m = /^\d{4}-\d{2}-\d{2}T(\d{2}):(\d{2})/.exec(iso);
    return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

const pad2 = (n: number): string => (n < 10 ? "0" : "") + n;
export const toIso = (d: Ymd): string => `${d.y}-${pad2(d.m)}-${pad2(d.d)}`;

function epochDays(d: Ymd): number { return Date.UTC(d.y, d.m - 1, d.d) / 86400000; }
function fromEpochDays(n: number): Ymd {
    const t = new Date(n * 86400000);
    return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate() };
}
export const addDays = (d: Ymd, n: number): Ymd => fromEpochDays(epochDays(d) + n);
const diffDays = (a: Ymd, b: Ymd): number => epochDays(b) - epochDays(a);
const compare = (a: Ymd, b: Ymd): number => epochDays(a) - epochDays(b);

/** ISO weekday: 0 = Monday … 6 = Sunday. */
function weekday(d: Ymd): number { return (new Date(Date.UTC(d.y, d.m - 1, d.d)).getUTCDay() + 6) % 7; }
function daysInMonth(y: number, m: number): number { return new Date(Date.UTC(y, m, 0)).getUTCDate(); }

/** Same day of the next or previous month, clamped to that month's length. */
export function addMonths(d: Ymd, n: number): Ymd {
    const total = d.y * 12 + (d.m - 1) + n;
    const y = Math.floor(total / 12), m = total - y * 12 + 1;
    return { y, m, d: Math.min(d.d, daysInMonth(y, m)) };
}

const hhmm = (minutes: number): string => `${pad2(Math.floor(minutes / 60))}:${pad2(minutes % 60)}`;

// ── Defaults ────────────────────────────────────────────────────────────────

const EN: Required<CalendarLabels> = {
    weekdays: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
    weekdaysLong: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
    months: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
    monthsShort: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
    today: "Today", day: "Day", week: "Week", month: "Month", allDay: "All day",
    more: "+{n} more", previous: "Previous", next: "Next",
};

function labelsOf(node: UiCalendarWire): Required<CalendarLabels> {
    const l = node.labels ?? {};
    const list = (own: string[] | undefined, n: number, fallback: string[]): string[] =>
        own && own.length === n ? own : fallback;
    return {
        weekdays: list(l.weekdays, 7, EN.weekdays),
        weekdaysLong: list(l.weekdaysLong, 7, EN.weekdaysLong),
        months: list(l.months, 12, EN.months),
        monthsShort: list(l.monthsShort, 12, EN.monthsShort),
        today: l.today ?? EN.today, day: l.day ?? EN.day, week: l.week ?? EN.week,
        month: l.month ?? EN.month, allDay: l.allDay ?? EN.allDay, more: l.more ?? EN.more,
        previous: l.previous ?? EN.previous, next: l.next ?? EN.next,
    };
}

// ── Events, normalised ──────────────────────────────────────────────────────

interface Ev {
    node: UiCalendarEventWire;
    start: Ymd;
    end: Ymd;
    /** Minutes past midnight, or null for an all-day event. */
    startMin: number | null;
    endMin: number | null;
    allDay: boolean;
}

function normalise(events: UiCalendarEventWire[]): Ev[] {
    const out: Ev[] = [];
    for (const node of events) {
        if (!node.start) continue;
        let start: Ymd;
        try { start = parseDate(node.start); } catch { continue; }
        const startMin = parseMinutes(node.start);
        const allDay = node.allDay ?? startMin === null;
        let end = start, endMin: number | null = null;
        if (node.end) {
            try { end = parseDate(node.end); } catch { end = start; }
            endMin = parseMinutes(node.end);
        }
        if (allDay) {
            if (compare(end, start) < 0) end = start;
            out.push({ node, start, end, startMin: null, endMin: null, allDay: true });
        } else {
            const s = startMin ?? 0;
            // A timed event lives on its start day; one that runs past
            // midnight is cut there, and one without an end lasts an hour.
            let e = compare(end, start) === 0 && endMin !== null ? endMin : (compare(end, start) > 0 ? 1440 : s + 60);
            if (e <= s) e = Math.min(1440, s + 60);
            out.push({ node, start, end: start, startMin: s, endMin: e, allDay: false });
        }
    }
    return out;
}

/** The events on a day, all-day ones first, then by start; ties keep the model's order. */
function onDay(events: Ev[], day: Ymd): Ev[] {
    return events
        .filter(e => e.allDay ? compare(e.start, day) <= 0 && compare(day, e.end) <= 0 : compare(e.start, day) === 0)
        .sort((a, b) => (a.allDay ? 0 : 1) - (b.allDay ? 0 : 1) || (a.startMin ?? 0) - (b.startMin ?? 0));
}

// ── Markup ──────────────────────────────────────────────────────────────────
// Mirrored line for line by CalendarPainter.java; the parity test holds both
// to calendar.expected-*.html.

function accent(color: string | undefined): string {
    return color ? ` style="--sui-calendar-accent:${esc(color)}"` : "";
}

function fill(t: Trigger, values: Record<string, string>): Trigger {
    let url = t.url ?? "";
    for (const [k, v] of Object.entries(values)) url = url.split(`{${k}}`).join(v);
    return { ...t, url };
}

/** A chip in a list: month cell or all-day row. A continuation day gets a derived id. */
function chip(e: Ev, day: Ymd): string {
    const first = compare(e.start, day) === 0;
    const id = first ? esc(e.node.id) : `${esc(e.node.id)}__${toIso(day)}`;
    const time = e.allDay ? "" : `<span class="sui-calendar-event-time">${hhmm(e.startMin!)}</span>`;
    return `<li class="${cls("sui-calendar-event", e.node)}${e.allDay ? " is-allday" : ""}"${evt(e.node)} id="${id}" data-sui="calendar-event"${accent(e.node.color)}>`
        + `${time}<span class="sui-calendar-event-title">${esc(e.node.title)}</span></li>`;
}

/** A timed event placed in a day column, in minutes from the column's first hour. */
function block(e: Ev, startHour: number, endHour: number): string {
    const lo = startHour * 60, hi = endHour * 60;
    const s = Math.max(lo, e.startMin!), en = Math.min(hi, e.endMin!);
    if (en <= s) return "";
    // One style attribute: the accent (when there is one) and the position.
    const style = (e.node.color ? `--sui-calendar-accent:${esc(e.node.color)};` : "")
        + `--sui-calendar-start:${s - lo};--sui-calendar-length:${Math.max(15, en - s)}`;
    return `<div class="${cls("sui-calendar-event", e.node)}"${evt(e.node)} id="${esc(e.node.id)}" data-sui="calendar-event" style="${style}">`
        + `<span class="sui-calendar-event-time">${hhmm(e.startMin!)}</span><span class="sui-calendar-event-title">${esc(e.node.title)}</span></div>`;
}

interface Ctx {
    node: UiCalendarWire;
    view: CalendarView;
    date: Ymd;
    today: Ymd | null;
    selected: Ymd | null;
    sundayFirst: boolean;
    startHour: number;
    endHour: number;
    labels: Required<CalendarLabels>;
    events: Ev[];
}

function context(node: UiCalendarWire): Ctx {
    const view: CalendarView = node.view === "DAY" || node.view === "WEEK" ? node.view : "MONTH";
    const parse = (s: string | undefined): Ymd | null => { if (!s) return null; try { return parseDate(s); } catch { return null; } };
    const today = parse(node.today);
    const startHour = Math.min(23, Math.max(0, node.startHour ?? 0));
    const endHour = Math.min(24, Math.max(startHour + 1, node.endHour ?? 24));
    return {
        node, view,
        date: parse(node.date) ?? today ?? { y: 1970, m: 1, d: 1 },
        today, selected: parse(node.selectedDate),
        sundayFirst: node.weekStart === "SUNDAY",
        startHour, endHour,
        labels: labelsOf(node),
        events: normalise(node.events ?? []),
    };
}

/** Column of a date in the week, 0–6, honouring the week start. */
const column = (c: Ctx, d: Ymd): number => c.sundayFirst ? (weekday(d) + 1) % 7 : weekday(d);
const weekOf = (c: Ctx, d: Ymd): Ymd => addDays(d, -column(c, d));

function dayState(c: Ctx, d: Ymd): string {
    return (c.today && compare(c.today, d) === 0 ? " is-today" : "")
        + (c.selected && compare(c.selected, d) === 0 ? " is-selected" : "");
}

function heading(c: Ctx): string {
    const { labels: l, date: d } = c;
    if (c.view === "DAY") return `${l.weekdaysLong[weekday(d)]}, ${d.d} ${l.months[d.m - 1]} ${d.y}`;
    if (c.view === "WEEK") {
        const a = weekOf(c, d), b = addDays(a, 6);
        return a.m === b.m
            ? `${a.d} – ${b.d} ${l.monthsShort[b.m - 1]} ${b.y}`
            : `${a.d} ${l.monthsShort[a.m - 1]} – ${b.d} ${l.monthsShort[b.m - 1]} ${b.y}`;
    }
    return `${l.months[d.m - 1]} ${d.y}`;
}

function shift(c: Ctx, n: number): Ymd {
    return c.view === "DAY" ? addDays(c.date, n) : c.view === "WEEK" ? addDays(c.date, 7 * n) : addMonths(c.date, n);
}

/**
 * The header: previous / today / next, the title, and the view switch. Every
 * button carries where it goes as `data-nav-date` / `data-nav-view`, which
 * the `<sui-calendar>` element turns into a re-render from the model it
 * already has — no server needed. With an `onNavigate` it carries the
 * trigger as well, so the server hears and may answer with the events of
 * the new period.
 */
function header(c: Ctx): string {
    const t = c.node.onNavigate;
    const title = `<h2 class="sui-calendar-title">${esc(heading(c))}</h2>`;
    const btn = (label: string, date: string, view: CalendarView, extra = "", aria = ""): string =>
        `<a class="sui-calendar-btn${extra}" href="#" data-nav-date="${date}" data-nav-view="${view}"`
        + (t ? ` data-trigger='${encodeTrigger(fill(t, { date, view }))}'` : "") + aria + `>${label}</a>`;
    const nav = `<div class="sui-calendar-nav">`
        + btn("&lsaquo;", toIso(shift(c, -1)), c.view, "", ` aria-label="${esc(c.labels.previous)}"`)
        + (c.today ? btn(esc(c.labels.today), toIso(c.today), c.view) : "")
        + btn("&rsaquo;", toIso(shift(c, 1)), c.view, "", ` aria-label="${esc(c.labels.next)}"`)
        + `</div>`;
    const views = `<div class="sui-calendar-views">`
        + (["DAY", "WEEK", "MONTH"] as CalendarView[]).map(v =>
            btn(esc(v === "DAY" ? c.labels.day : v === "WEEK" ? c.labels.week : c.labels.month),
                toIso(c.date), v, v === c.view ? " is-active" : "")).join("")
        + `</div>`;
    return `<header class="sui-calendar-head">${nav}${title}${views}</header>`;
}

function weekdayHeads(c: Ctx, first: Ymd, withDates: boolean): string {
    const cells: string[] = [];
    for (let i = 0; i < 7; i++) {
        const d = addDays(first, i);
        const name = esc(c.labels.weekdays[weekday(d)]);
        cells.push(withDates
            ? `<span class="sui-calendar-weekday${dayState(c, d)}" role="columnheader" data-date="${toIso(d)}">${name} ${d.d}</span>`
            : `<span class="sui-calendar-weekday" role="columnheader">${name}</span>`);
    }
    return cells.join("");
}

function monthBody(c: Ctx): string {
    const first = { y: c.date.y, m: c.date.m, d: 1 };
    const last = { y: c.date.y, m: c.date.m, d: daysInMonth(c.date.y, c.date.m) };
    const gridStart = weekOf(c, first);
    const gridEnd = addDays(last, 6 - column(c, last));
    const weeks = (diffDays(gridStart, gridEnd) + 1) / 7;
    const max = Math.max(1, c.node.maxEventsPerDay ?? 3);
    let rows = "";
    for (let w = 0; w < weeks; w++) {
        let cells = "";
        for (let i = 0; i < 7; i++) {
            const d = addDays(gridStart, w * 7 + i);
            const evs = onDay(c.events, d);
            const shown = evs.slice(0, max).map(e => chip(e, d)).join("");
            const more = evs.length > max
                ? `<span class="sui-calendar-more">${esc(c.labels.more.split("{n}").join(String(evs.length - max)))}</span>`
                : "";
            const outside = d.m !== c.date.m ? " is-outside" : "";
            cells += `<div class="sui-calendar-day${outside}${dayState(c, d)}" role="gridcell" data-date="${toIso(d)}">`
                + `<span class="sui-calendar-daynum">${d.d}</span><ul class="sui-calendar-events">${shown}</ul>${more}</div>`;
        }
        rows += `<div class="sui-calendar-week" role="row">${cells}</div>`;
    }
    return `<div class="sui-calendar-month" role="grid">`
        + `<div class="sui-calendar-weekdays" role="row">${weekdayHeads(c, gridStart, false)}</div>${rows}</div>`;
}

function timeBody(c: Ctx): string {
    const days = c.view === "DAY" ? 1 : 7;
    const first = c.view === "DAY" ? c.date : weekOf(c, c.date);
    const hours = c.endHour - c.startHour;
    let heads = "", allday = "", cols = "";
    for (let i = 0; i < days; i++) {
        const d = addDays(first, i);
        const evs = onDay(c.events, d);
        const name = esc(c.labels.weekdays[weekday(d)]);
        heads += `<span class="sui-calendar-weekday${dayState(c, d)}" role="columnheader" data-date="${toIso(d)}">${name} ${d.d}</span>`;
        allday += `<div class="sui-calendar-allday-day${dayState(c, d)}" role="gridcell" data-date="${toIso(d)}">`
            + `<ul class="sui-calendar-events">${evs.filter(e => e.allDay).map(e => chip(e, d)).join("")}</ul></div>`;
        let slots = "";
        for (let h = c.startHour; h < c.endHour; h++) slots += `<div class="sui-calendar-slot" data-hour="${h}"></div>`;
        cols += `<div class="sui-calendar-daycol${dayState(c, d)}" role="gridcell" data-date="${toIso(d)}">${slots}`
            + evs.filter(e => !e.allDay).map(e => block(e, c.startHour, c.endHour)).join("") + `</div>`;
    }
    let hourLabels = "";
    for (let h = c.startHour; h < c.endHour; h++) hourLabels += `<span class="sui-calendar-hour">${hhmm(h * 60)}</span>`;
    return `<div class="sui-calendar-timegrid sui-calendar-timegrid--${c.view.toLowerCase()}" role="grid" style="--sui-calendar-hours:${hours}">`
        + `<div class="sui-calendar-weekdays" role="row"><span class="sui-calendar-corner"></span>${heads}</div>`
        + `<div class="sui-calendar-allday" role="row"><span class="sui-calendar-corner">${esc(c.labels.allDay)}</span>${allday}</div>`
        + `<div class="sui-calendar-times"><div class="sui-calendar-hours">${hourLabels}</div>${cols}</div></div>`;
}

/** The node behind each rendered calendar, by id — what a client-side navigation re-renders from. */
const models = new Map<string, UiCalendarWire>();
let rendererRef: SuiRenderer | null = null;

export function renderCalendar(node: UiCalendarWire): string {
    models.set(node.id, node);
    const c = context(node);
    const select = node.onSelect ? ` data-select-trigger='${encodeTrigger(node.onSelect)}'` : "";
    return `<sui-calendar class="${cls(`sui-calendar sui-calendar--${c.view.toLowerCase()}`, node)}"${evt(node)} id="${esc(node.id)}" data-sui="calendar"`
        + ` data-view="${c.view}" data-date="${toIso(c.date)}"${select}>`
        + header(c) + `<div class="sui-calendar-body">${c.view === "MONTH" ? monthBody(c) : timeBody(c)}</div></sui-calendar>`;
}

// ── Picking a day or an hour ────────────────────────────────────────────────

let bus: CalendarBus | null = null;

/**
 * Registers the `calendar` painter on a renderer and, in a browser, defines
 * the `<sui-calendar>` element that turns a click on a day or an hour slot
 * into the calendar's `onSelect` trigger — `{date}` and `{hour}` filled in.
 *
 * <p>Previous / next / today and the view switch re-render the calendar from
 * the model it was drawn from, so they work with no server at all; with an
 * `onNavigate` the trigger fires as well, through the bus when one is passed
 * and through the page's own event bus otherwise. Pass the bus so picking a
 * day goes through it too; without one the element emits a
 * `sui-calendar-select` event (`detail: { date, hour }`) instead.
 */
export function install(renderer: SuiRenderer, options: { bus?: CalendarBus } = {}): void {
    renderer.register<UiCalendarWire>("calendar", renderCalendar);
    rendererRef = renderer;
    if (options.bus) bus = options.bus;
    defineElement();
}

/** The trigger to fire for a pick: `onSelect` with `{date}` and `{hour}` filled in. */
export function selectTrigger(template: Trigger, date: string, hour: number | null): Trigger {
    return fill(template, { date, hour: hour === null ? "" : String(hour) });
}

function defineElement(): void {
    if (typeof HTMLElement === "undefined" || typeof customElements === "undefined") return;
    if (customElements.get("sui-calendar")) return;

    class SuiCalendarElement extends HTMLElement {
        private wired = false;

        connectedCallback(): void {
            if (this.wired) return;
            this.wired = true;
            this.addEventListener("click", e => this.onClick(e));
        }

        private navigate(e: MouseEvent, btn: HTMLElement): void { navigateFrom(this, e, btn); }

        private onClick(e: MouseEvent): void {
            const target = e.target instanceof Element ? e.target : null;
            if (!target) return;
            const nav = target.closest<HTMLElement>(".sui-calendar-btn[data-nav-date]");
            if (nav && this.contains(nav)) { this.navigate(e, nav); return; }
            if (target.closest(".sui-calendar-event, .sui-calendar-btn, a, button")) return;
            const slot = target.closest<HTMLElement>(".sui-calendar-slot");
            const cell = target.closest<HTMLElement>(".sui-calendar-day, .sui-calendar-allday-day, .sui-calendar-daycol");
            if (!cell || !this.contains(cell)) return;
            const date = cell.dataset.date;
            if (!date) return;
            const hour = slot?.dataset.hour !== undefined ? Number(slot.dataset.hour) : null;
            e.preventDefault();
            this.dispatchEvent(new CustomEvent("sui-calendar-select", { detail: { date, hour }, bubbles: true, composed: true }));
            const raw = this.getAttribute("data-select-trigger");
            if (!raw || !bus) return;
            let template: Trigger;
            try { template = JSON.parse(raw) as Trigger; }
            catch (err) { console.error("sui-calendar: bad data-select-trigger JSON", err, raw); return; }
            void bus.dispatch(selectTrigger(template, date, hour), slot ?? cell);
        }
    }

    customElements.define("sui-calendar", SuiCalendarElement);
}

/**
 * A navigation button pressed: the calendar re-renders itself for the new
 * date and view from the model it has, and fires the button's trigger, if
 * any — through the bus it was given, or by leaving the click to the page's
 * own bus. The re-render is deferred a tick so that a bus looking at the
 * click still finds the button in the page.
 */
function navigateFrom(el: HTMLElement, e: MouseEvent, btn: HTMLElement): void {
    const node = models.get(el.id);
    const date = btn.dataset.navDate, view = btn.dataset.navView as CalendarView | undefined;
    if (!node || !rendererRef || !date || !view) return;   // not ours to handle: the trigger link stands
    const raw = btn.getAttribute("data-trigger");
    let trigger: Trigger | null = null;
    if (raw && bus) {
        try { trigger = JSON.parse(raw) as Trigger; } catch { trigger = null; }
    }
    const next: UiCalendarWire = { ...node, date, view };
    if (trigger) { e.preventDefault(); e.stopPropagation(); }
    else if (!raw) e.preventDefault();
    const id = el.id;
    queueMicrotask(() => {
        const current = document.getElementById(id);
        if (!current) return;
        current.outerHTML = rendererRef!.render(next as never);
        const fresh = document.getElementById(id) ?? undefined;
        if (trigger && bus) void bus.dispatch(trigger, fresh);
    });
}
