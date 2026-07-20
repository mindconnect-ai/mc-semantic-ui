import type { SuiRenderer } from "/sui/renderer.js";

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

/** Wire shape of the core's `chart` node — mirrors UiChart on the Java side. */
interface UiChartWire {
    type: "chart";
    id: string;
    title?: string;
    cssClass?: string;
    chartType?: "LINE" | "BAR" | "PIE" | "DONUT" | "AREA";
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

/**
 * Registers the chart painter on a renderer, replacing the core's placeholder.
 *
 * <p>The core ships `chart` as a node type but draws nothing — it emits an
 * empty `<div class="sui-chart" data-chart='…'>` so an addon can take over.
 * This is that addon: one `register()` call, no charting library, plain SVG.
 *
 * <p>Scoped to the renderer you pass in; nothing global is patched.
 */
export function install(renderer: SuiRenderer): void {
    renderer.register<UiChartWire>("chart", renderChart);
}

export function renderChart(node: UiChartWire): string {
    const cls = node.cssClass ? `sui-chart ${esc(node.cssClass)}` : "sui-chart";
    const title = node.title ? `<h2>${esc(node.title)}</h2>` : "";
    return `<div class="${cls}" id="${esc(node.id)}" data-sui="chart">${title}${chartSvg(node)}</div>`;
}

/** The drawing itself — shared entry point, mirrored by ChartPainter.java. */
export function chartSvg(node: UiChartWire): string {
    const labels = node.data?.labels ?? [];
    const series = node.data?.series ?? [];
    const values = series[0]?.values ?? [];
    if (values.length === 0) {
        return `<p class="sui-chart-empty">No data.</p>`;
    }
    const type = node.chartType ?? "BAR";
    return (type === "PIE" || type === "DONUT")
        ? radialSvg(values, labels, type === "DONUT")
        : cartesianSvg(values, labels, type);
}

// ── Cartesian: BAR / LINE / AREA ────────────────────────────────────────────

function cartesianSvg(values: number[], labels: string[], type: string): string {
    const W = 320, H = 170, P = 30;
    const iw = W - 2 * P, ih = H - 2 * P;
    const max = Math.max(1, ...values);
    const n = values.length;

    const axis = `<line x1="${P}" y1="${P + ih}" x2="${P + iw}" y2="${P + ih}" `
        + `stroke="var(--sui-color-border-strong, #94a3b8)"/>`;

    const xLabels = labels.map((lb, i) => {
        const x = P + (type === "BAR"
            ? (i + 0.5) * (iw / n)
            : (n === 1 ? iw / 2 : (iw * i) / (n - 1)));
        return `<text x="${x.toFixed(1)}" y="${H - 8}" font-size="10" text-anchor="middle" `
            + `fill="var(--sui-color-text-muted, #64748b)">${esc(lb)}</text>`;
    }).join("");

    let plot: string;
    if (type === "BAR") {
        const gap = iw / n, bw = gap * 0.6;
        plot = values.map((v, i) => {
            const bh = ih * (v / max);
            const x = P + i * gap + (gap - bw) / 2;
            const y = P + ih - bh;
            // <title> gives every bar a native tooltip — no JS, works in SSR.
            return `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${bw.toFixed(1)}" `
                + `height="${bh.toFixed(1)}" rx="2" fill="${PALETTE[0]}">`
                + `<title>${esc(labels[i] ?? "")}: ${v}</title></rect>`;
        }).join("");
    } else {
        const pts = values.map((v, i) => {
            const x = P + (n === 1 ? iw / 2 : (iw * i) / (n - 1));
            const y = P + ih - ih * (v / max);
            return `${x.toFixed(1)},${y.toFixed(1)}`;
        });
        const poly = pts.join(" ");
        const area = type === "AREA"
            ? `<polygon points="${P},${P + ih} ${poly} ${P + iw},${P + ih}" `
                + `fill="var(--sui-color-primary-soft, #dbeafe)"/>`
            : "";
        const dots = values.map((v, i) => {
            const [x, y] = pts[i]!.split(",");
            return `<circle cx="${x}" cy="${y}" r="3" fill="${PALETTE[0]}">`
                + `<title>${esc(labels[i] ?? "")}: ${v}</title></circle>`;
        }).join("");
        plot = `${area}<polyline points="${poly}" fill="none" stroke="${PALETTE[0]}" stroke-width="2"/>${dots}`;
    }
    return `<svg class="sui-chart-svg" viewBox="0 0 ${W} ${H}" role="img" `
        + `aria-label="${esc(type.toLowerCase())} chart">${axis}${plot}${xLabels}</svg>`;
}

// ── Radial: PIE / DONUT ─────────────────────────────────────────────────────

function radialSvg(values: number[], labels: string[], donut: boolean): string {
    const cx = 85, cy = 85;
    const r = donut ? 60 : 42;
    // A pie is a donut whose stroke is wide enough to close the hole.
    const sw = donut ? 20 : 84;
    const C = 2 * Math.PI * r;
    const total = values.reduce((a, b) => a + b, 0) || 1;

    let offset = 0;
    const segs = values.map((v, i) => {
        const len = (v / total) * C;
        const seg = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" `
            + `stroke="${PALETTE[i % PALETTE.length]}" stroke-width="${sw}" `
            + `stroke-dasharray="${len.toFixed(2)} ${(C - len).toFixed(2)}" `
            + `stroke-dashoffset="${(-offset).toFixed(2)}" transform="rotate(-90 ${cx} ${cy})">`
            + `<title>${esc(labels[i] ?? "")}: ${v}</title></circle>`;
        offset += len;
        return seg;
    }).join("");

    const legend = labels.map((lb, i) =>
        `<li><span class="sui-chart-swatch" style="background:${PALETTE[i % PALETTE.length]}"></span>${esc(lb)}</li>`
    ).join("");

    return `<div class="sui-chart-radial">`
        + `<svg class="sui-chart-svg" viewBox="0 0 170 170" role="img" `
        + `aria-label="${donut ? "donut" : "pie"} chart">${segs}</svg>`
        + `<ul class="sui-chart-legend">${legend}</ul>`
        + `</div>`;
}
