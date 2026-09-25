import type { SuiRenderer } from "@mindconnect-ai/mc-semantic-ui-core";

// escapeHtml is inlined (not imported from the core bundle) so the compiled
// extension.js has NO runtime import of /sui/renderer.js. That keeps the bundle
// portable: it works from a CDN or under a path prefix, where an absolute
// /sui/ import would resolve against the wrong origin. The `import type` above
// is erased at compile time. Same trick as the diagram extension.
const HTML_ESCAPE: Record<string, string> =
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
function esc(value: unknown): string {
    if (value == null) return "";
    return String(value).replace(/[&<>"']/g, ch => HTML_ESCAPE[ch]!);
}

/** How values are written in tooltips, legends and on the value axis. */
interface ValueFormat {
    prefix?: string;
    suffix?: string;
    decimals?: number;
}

/** Wire shape of the core's `chart` node — mirrors UiChart on the Java side. */
interface UiChartWire {
    type: "chart";
    id: string;
    title?: string;
    cssClass?: string;
    chartType?: "LINE" | "BAR" | "PIE" | "DONUT" | "AREA";
    stacked?: boolean;
    crosshair?: boolean;
    valueFormat?: ValueFormat;
    data?: {
        labels?: string[];
        series?: Array<{ name?: string; values?: number[] }>;
    };
}

/**
 * Categorical palette. Deliberately CSS variables with literal fallbacks: an
 * app can restyle every chart from its own stylesheet, and the same markup
 * still renders standalone (in an email, a PDF export, a CodePen).
 */
const PALETTE = [
    "var(--sui-chart-1, #4f6bed)",
    "var(--sui-chart-2, #29a3a3)",
    "var(--sui-chart-3, #e0a300)",
    "var(--sui-chart-4, #c2410c)",
    "var(--sui-chart-5, #7c3aed)",
    "var(--sui-chart-6, #0891b2)",
];

function color(i: number): string {
    return PALETTE[i % PALETTE.length]!;
}

/**
 * Registers the chart painter on a renderer, replacing the core's placeholder.
 *
 * <p>The core ships `chart` as a node type but draws nothing — it emits an
 * empty `<div class="sui-chart" data-chart='…'>` so an addon can take over.
 * This is that addon: one `register()` call, no charting library, plain SVG.
 *
 * <p>The drawing is scoped to the renderer you pass in. The hover — the
 * cursor line, the tooltip, the highlighted column — is one set of listeners
 * on the document, added once, so it also brings server-rendered charts to
 * life on a page that loads this script.
 */
export function install(renderer: SuiRenderer): void {
    renderer.register<UiChartWire>("chart", renderChart);
    enableHover();
}

export function renderChart(node: UiChartWire): string {
    const cls = node.cssClass ? `sui-chart ${esc(node.cssClass)}` : "sui-chart";
    const title = node.title ? `<h2>${esc(node.title)}</h2>` : "";
    return `<div class="${cls}" id="${esc(node.id)}" data-sui="chart">${title}${chartSvg(node)}</div>`;
}

/** The drawing itself — shared entry point, mirrored by ChartPainter.java. */
export function chartSvg(node: UiChartWire): string {
    const labels = node.data?.labels ?? [];
    const series = (node.data?.series ?? []).filter(s => (s.values ?? []).length > 0);
    if (series.length === 0) {
        return `<p class="sui-chart-empty">No data.</p>`;
    }
    const type = node.chartType ?? "BAR";
    const format = node.valueFormat ?? {};
    return (type === "PIE" || type === "DONUT")
        ? radialSvg(series[0]!.values!, labels, type === "DONUT", format)
        : cartesianSvg(series, labels, type, node.stacked === true, node.crosshair !== false, format);
}

// ── Numbers: must match ChartPainter.java character for character ───────────

/** toFixed without a negative zero: -0.00 and 0.00 are the same drawing. */
function fixed(v: number, d: number): string {
    return (v === 0 ? 0 : v).toFixed(d);
}

/** Thousands separated by commas: "1234567.5" → "1,234,567.5". */
function grouped(text: string): string {
    const neg = text.startsWith("-");
    const body = neg ? text.slice(1) : text;
    const dot = body.indexOf(".");
    const int = dot < 0 ? body : body.slice(0, dot);
    const frac = dot < 0 ? "" : body.slice(dot);
    let out = "";
    for (let i = 0; i < int.length; i++) {
        if (i > 0 && (int.length - i) % 3 === 0) out += ",";
        out += int[i];
    }
    return (neg ? "-" : "") + out + frac;
}

/** Trailing zeros off a decimal, down to {@code keep} places. */
function trimZeros(text: string, keep: number): string {
    const dot = text.indexOf(".");
    if (dot < 0) return text;
    let end = text.length;
    while (end > dot + 1 + keep && text[end - 1] === "0") end--;
    if (end === dot + 1) end = dot;
    return text.slice(0, end);
}

/**
 * A value as a person reads it: grouped, with the format's prefix and
 * suffix. Without fixed decimals a whole number gets none, a number of at
 * least one two, and a smaller one up to four — a day's cost of 0.0034 is
 * not "0.00".
 */
function formatValue(v: number, format: ValueFormat): string {
    let text: string;
    if (format.decimals != null) {
        text = fixed(v, format.decimals);
    } else if (Number.isInteger(v)) {
        text = fixed(v, 0);
    } else if (Math.abs(v) >= 1) {
        text = fixed(v, 2);
    } else {
        text = trimZeros(fixed(v, 4), 2);
    }
    return (format.prefix ?? "") + grouped(text) + (format.suffix ?? "");
}

/** How many decimals a tick step needs: 0.25 → 2, 5 → 0. */
function stepDecimals(step: number): number {
    let d = 0;
    let s = step;
    while (d < 6 && Math.abs(s - Math.round(s)) > 1e-9) {
        s *= 10;
        d++;
    }
    return d;
}

/** An axis tick, short: 250000 → "250k", 1500000 → "1.5M". The prefix stays, the suffix is the title's job. */
function formatTick(v: number, step: number, format: ValueFormat): string {
    const a = Math.abs(v);
    let text: string;
    if (a >= 1e9) text = trimZeros(fixed(v / 1e9, 2), 0) + "B";
    else if (a >= 1e6) text = trimZeros(fixed(v / 1e6, 2), 0) + "M";
    else if (a >= 1e3) text = trimZeros(fixed(v / 1e3, 2), 0) + "k";
    else text = fixed(v, stepDecimals(step));
    return (format.prefix ?? "") + text;
}

/**
 * A round step for about four intervals up to {@code max}: 1, 2, 2.5 or 5
 * times a power of ten. The power is found by multiplying and dividing by
 * ten, not with Math.pow, so Java and JavaScript land on the same double.
 */
function niceStep(max: number): number {
    const rough = max / 4;
    let mag = 1;
    while (mag * 10 <= rough) mag *= 10;
    while (mag > rough) mag /= 10;
    const norm = rough / mag;
    const m = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10;
    return m * mag;
}

/** A JSON array of strings, written the same way on both sides. */
function jsonStrings(values: string[]): string {
    return "[" + values.map(v => {
        let out = "\"";
        for (const ch of v) {
            const c = ch.charCodeAt(0);
            if (ch === "\"" || ch === "\\") out += "\\" + ch;
            else if (c < 0x20) out += "\\u" + c.toString(16).padStart(4, "0");
            else out += ch;
        }
        return out + "\"";
    }).join(",") + "]";
}

// ── Cartesian: BAR / LINE / AREA ────────────────────────────────────────────

const W = 560, H = 240, PL = 48, PR = 12, PT = 14, PB = 30;
const IW = W - PL - PR, IH = H - PT - PB;

function cartesianSvg(series: Array<{ name?: string; values?: number[] }>, labels: string[],
                      type: string, stackedWanted: boolean, crosshair: boolean, format: ValueFormat): string {
    const bar = type === "BAR";
    const stacked = bar && stackedWanted && series.length > 1;
    const n = Math.max(...series.map(s => s.values!.length));
    const value = (s: number, i: number): number => Math.max(0, series[s]!.values![i] ?? 0);

    let max = 0;
    for (let i = 0; i < n; i++) {
        if (stacked) {
            let sum = 0;
            for (let s = 0; s < series.length; s++) sum += value(s, i);
            max = Math.max(max, sum);
        } else {
            for (let s = 0; s < series.length; s++) max = Math.max(max, value(s, i));
        }
    }
    if (max <= 0) max = 1;
    const step = niceStep(max);
    const ticks = Math.ceil(max / step - 1e-9);
    const top = ticks * step;
    const y = (v: number): number => PT + IH - IH * (v / top);
    const gap = IW / n;
    const cx = (i: number): number => bar
        ? PL + (i + 0.5) * gap
        : PL + (n === 1 ? IW / 2 : (IW * i) / (n - 1));

    let grid = "";
    for (let t = 0; t <= ticks; t++) {
        const v = t * step;
        const ty = y(v);
        if (t > 0) {
            grid += `<line class="sui-chart-gridline" x1="${PL}" y1="${ty.toFixed(1)}" x2="${PL + IW}" `
                + `y2="${ty.toFixed(1)}" stroke="var(--sui-color-border, #e2e8f0)"/>`;
        }
        grid += `<text x="${PL - 6}" y="${(ty + 3.5).toFixed(1)}" font-size="10" text-anchor="end" `
            + `fill="var(--sui-color-text-muted, #64748b)">${esc(formatTick(v, step, format))}</text>`;
    }
    const axis = `<line x1="${PL}" y1="${PT + IH}" x2="${PL + IW}" y2="${PT + IH}" `
        + `stroke="var(--sui-color-border-strong, #94a3b8)"/>`;

    let plot = "";
    if (bar) {
        const group = gap * 0.7;
        for (let i = 0; i < n; i++) {
            const left = PL + i * gap + (gap - group) / 2;
            let base = 0;
            for (let s = 0; s < series.length; s++) {
                const v = value(s, i);
                const bw = stacked ? group : group / series.length;
                const x = stacked ? left : left + s * bw;
                const from = stacked ? base : 0;
                const yTop = y(from + v);
                const h = y(from) - yTop;
                plot += `<rect class="sui-chart-bar" data-col="${i}" x="${x.toFixed(1)}" y="${yTop.toFixed(1)}" `
                    + `width="${bw.toFixed(1)}" height="${h.toFixed(1)}" rx="2" fill="${color(s)}"/>`;
                if (stacked) base += v;
            }
        }
    } else {
        for (let s = 0; s < series.length; s++) {
            const pts: string[] = [];
            for (let i = 0; i < n; i++) pts.push(`${cx(i).toFixed(1)},${y(value(s, i)).toFixed(1)}`);
            const poly = pts.join(" ");
            if (type === "AREA") {
                plot += `<polygon points="${PL},${PT + IH} ${poly} ${PL + IW},${PT + IH}" `
                    + `fill="${color(s)}" fill-opacity="0.15"/>`;
            }
            plot += `<polyline points="${poly}" fill="none" stroke="${color(s)}" stroke-width="2"/>`;
            for (let i = 0; i < n; i++) {
                plot += `<circle class="sui-chart-dot" data-col="${i}" cx="${cx(i).toFixed(1)}" `
                    + `cy="${y(value(s, i)).toFixed(1)}" r="3" fill="${color(s)}"/>`;
            }
        }
    }

    // Every label only when they fit; a month of days shows every few.
    const every = Math.max(1, Math.ceil(n / Math.max(1, Math.floor(IW / 70))));
    let xLabels = "";
    for (let i = 0; i < labels.length && i < n; i++) {
        if (i % every !== 0) continue;
        xLabels += `<text x="${cx(i).toFixed(1)}" y="${H - 10}" font-size="10" text-anchor="middle" `
            + `fill="var(--sui-color-text-muted, #64748b)">${esc(labels[i])}</text>`;
    }

    // The line down the hovered column; off, the column's own highlight is all.
    const cursor = crosshair
        ? `<line class="sui-chart-cursor" x1="${PL}" y1="${PT}" x2="${PL}" y2="${PT + IH}"/>`
        : "";

    // One band per column over the whole height: the hover answers anywhere
    // above a day, not only on the bar's edge. The <title> is the tooltip
    // without JavaScript; the script takes it off and draws its own.
    const names = series.map((s, i) => s.name ?? `Series ${i + 1}`);
    let cols = "";
    for (let i = 0; i < n; i++) {
        const label = labels[i] ?? "";
        const shown = series.map((_, s) => formatValue(value(s, i), format));
        let total = 0;
        for (let s = 0; s < series.length; s++) total += value(s, i);
        const title = series.length === 1
            ? `${label}: ${shown[0]}`
            : `${label}: ` + names.map((nm, s) => `${nm} ${shown[s]}`).join(", ");
        const band = bar ? gap : (n === 1 ? IW : IW / (n - 1));
        const left = bar ? PL + i * gap : cx(i) - band / 2;
        cols += `<rect class="sui-chart-col" data-col="${i}" data-x="${cx(i).toFixed(1)}" `
            + `data-label="${esc(label)}" data-values="${esc(jsonStrings(shown))}"`
            + (stacked ? ` data-total="${esc(formatValue(total, format))}"` : "")
            + ` x="${left.toFixed(1)}" y="${PT}" width="${band.toFixed(1)}" height="${IH}" fill="transparent">`
            + `<title>${esc(title)}</title></rect>`;
    }

    const legend = series.length > 1
        ? `<ul class="sui-chart-legend sui-chart-legend--row">` + names.map((nm, s) =>
            `<li><span class="sui-chart-swatch" style="background:${color(s)}"></span>${esc(nm)}</li>`).join("")
            + `</ul>`
        : "";

    return `<svg class="sui-chart-svg sui-chart-cartesian" viewBox="0 0 ${W} ${H}" role="img" `
        + `aria-label="${esc(type.toLowerCase())} chart" data-series="${esc(jsonStrings(names))}">`
        + `${grid}${axis}${plot}${xLabels}${cursor}<g class="sui-chart-cols">${cols}</g></svg>${legend}`;
}

// ── Radial: PIE / DONUT ─────────────────────────────────────────────────────

function radialSvg(raw: number[], labels: string[], donut: boolean, format: ValueFormat): string {
    const values = raw.map(v => Math.max(0, v));
    const cx = 85, cy = 85;
    const r = donut ? 60 : 42;
    // A pie is a donut whose stroke is wide enough to close the hole.
    const sw = donut ? 20 : 84;
    const C = 2 * Math.PI * r;
    const total = values.reduce((a, b) => a + b, 0) || 1;
    const pct = (v: number): string => fixed((v / total) * 100, 0) + "%";

    let offset = 0;
    const segs = values.map((v, i) => {
        const len = (v / total) * C;
        const shown = formatValue(v, format);
        const seg = `<circle class="sui-chart-seg" data-col="${i}" data-label="${esc(labels[i] ?? "")}" `
            + `data-values="${esc(jsonStrings([shown]))}" data-pct="${pct(v)}" `
            + `cx="${cx}" cy="${cy}" r="${r}" fill="none" `
            + `stroke="${color(i)}" stroke-width="${sw}" `
            + `stroke-dasharray="${len.toFixed(2)} ${(C - len).toFixed(2)}" `
            + `stroke-dashoffset="${(-offset).toFixed(2)}" transform="rotate(-90 ${cx} ${cy})">`
            + `<title>${esc(labels[i] ?? "")}: ${esc(shown)} (${pct(v)})</title></circle>`;
        offset += len;
        return seg;
    }).join("");

    const legend = labels.map((lb, i) =>
        `<li data-col="${i}"><span class="sui-chart-swatch" style="background:${color(i)}"></span>`
        + `<span class="sui-chart-legend-name">${esc(lb)}</span>`
        + `<span class="sui-chart-legend-value">${esc(formatValue(values[i] ?? 0, format))} · ${pct(values[i] ?? 0)}</span></li>`
    ).join("");

    return `<div class="sui-chart-radial">`
        + `<svg class="sui-chart-svg" viewBox="0 0 170 170" role="img" `
        + `aria-label="${donut ? "donut" : "pie"} chart">${segs}</svg>`
        + `<ul class="sui-chart-legend">${legend}</ul>`
        + `</div>`;
}

// ── Hover: the cursor, the tooltip, the highlight ───────────────────────────

let hoverEnabled = false;

/**
 * Listens on the document once. A column band, a donut segment or a legend
 * row under the pointer marks everything of that index in its chart as
 * active — the CSS animates it — and fills the chart's tooltip.
 */
export function enableHover(): void {
    if (hoverEnabled || typeof document === "undefined") return;
    hoverEnabled = true;
    let current: Element | null = null;

    const clear = (): void => {
        if (!current) return;
        current.classList.remove("sui-chart--hover");
        current.querySelectorAll(".is-active").forEach(el => el.classList.remove("is-active"));
        const tip = current.querySelector<HTMLElement>(".sui-chart-tip");
        if (tip) tip.hidden = true;
        current = null;
    };

    document.addEventListener("pointermove", (e: PointerEvent) => {
        const target = e.target instanceof Element
            ? e.target.closest(".sui-chart-col, .sui-chart-seg, .sui-chart-legend li[data-col]")
            : null;
        const chart = target?.closest(".sui-chart") ?? null;
        if (!target || !chart) {
            clear();
            return;
        }
        if (chart !== current) clear();
        current = chart;
        // The native tooltips go the first time the script is there to draw its own.
        chart.querySelectorAll("title").forEach(t => t.remove());
        const col = target.getAttribute("data-col");
        chart.classList.add("sui-chart--hover");
        chart.querySelectorAll("[data-col]").forEach(el =>
            el.classList.toggle("is-active", el.getAttribute("data-col") === col));
        const source = target.matches("li")
            ? chart.querySelector(`.sui-chart-seg[data-col="${col}"]`) ?? target
            : target;
        showTip(chart as HTMLElement, source, e);
    }, { passive: true });

    document.addEventListener("pointerleave", clear);
    window.addEventListener("blur", clear);
}

function showTip(chart: HTMLElement, source: Element, e: PointerEvent): void {
    let tip = chart.querySelector<HTMLElement>(".sui-chart-tip");
    if (!tip) {
        tip = document.createElement("div");
        tip.className = "sui-chart-tip";
        tip.setAttribute("role", "tooltip");
        chart.appendChild(tip);
    }
    const svg = source.closest("svg");
    let names: string[] = [];
    let values: string[] = [];
    try {
        names = JSON.parse(svg?.getAttribute("data-series") ?? "[]");
        values = JSON.parse(source.getAttribute("data-values") ?? "[]");
    } catch {
        return;
    }
    const seg = source.classList.contains("sui-chart-seg");
    const index = Number(source.getAttribute("data-col"));
    let rows = "";
    values.forEach((v, s) => {
        const swatch = color(seg ? index : s);
        const name = seg ? (source.getAttribute("data-pct") ?? "") : (names[s] ?? "");
        rows += `<div class="sui-chart-tip-row"><span class="sui-chart-swatch" style="background:${swatch}"></span>`
            + `<span class="sui-chart-tip-name">${esc(name)}</span><span class="sui-chart-tip-value">${esc(v)}</span></div>`;
    });
    const total = source.getAttribute("data-total");
    if (total) {
        rows += `<div class="sui-chart-tip-row sui-chart-tip-total"><span class="sui-chart-tip-name">Total</span>`
            + `<span class="sui-chart-tip-value">${esc(total)}</span></div>`;
    }
    tip.innerHTML = `<div class="sui-chart-tip-label">${esc(source.getAttribute("data-label") ?? "")}</div>${rows}`;
    tip.hidden = false;

    const box = chart.getBoundingClientRect();
    let x: number;
    let top: number;
    if (!seg && svg) {
        // Cartesian: the cursor line on the column's centre, the tip beside it.
        const vb = svg.viewBox.baseVal;
        const r = svg.getBoundingClientRect();
        const scale = vb && vb.width ? r.width / vb.width : 1;
        const dataX = Number(source.getAttribute("data-x"));
        const cursor = svg.querySelector(".sui-chart-cursor");
        cursor?.setAttribute("x1", String(dataX));
        cursor?.setAttribute("x2", String(dataX));
        x = r.left - box.left + dataX * scale;
        top = r.top - box.top + PT * scale;
    } else {
        x = e.clientX - box.left;
        top = e.clientY - box.top - tip.offsetHeight / 2;
    }
    const w = tip.offsetWidth;
    const left = x + 14 + w > box.width ? x - 14 - w : x + 14;
    tip.style.left = `${Math.max(0, left)}px`;
    tip.style.top = `${Math.max(0, top)}px`;
}
