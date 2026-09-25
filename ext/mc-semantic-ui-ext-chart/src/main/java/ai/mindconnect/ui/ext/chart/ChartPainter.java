package ai.mindconnect.ui.ext.chart;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-side SVG painter for the core {@link UiChart} node — the Java twin of
 * {@code chartSvg()} in {@code src/main/ts/chart/extension.ts}.
 *
 * <p>The two must agree: a page rendered by the server and the same page
 * rendered in the browser have to look identical, which is the whole point of
 * the dual-render model. The geometry constants below are therefore duplicated
 * deliberately rather than derived — a shared constant would have to live in
 * one language and be shipped to the other, which costs more than it saves for
 * a dozen numbers. {@code ChartPainterGoldenTest} compares this painter's
 * output with the TypeScript one's, character for character.
 *
 * <p>Everything is plain SVG with {@code <title>} tooltips, so a chart rendered
 * this way needs <b>no JavaScript at all</b>. With the extension's script on
 * the page the same markup gets the hover: the column band, the cursor line,
 * the tooltip — which is what the {@code data-*} attributes are for.
 */
public final class ChartPainter {

    private ChartPainter() {}

    /** Categorical palette — CSS variables with literal fallbacks, as in the TS. */
    private static final List<String> PALETTE = List.of(
            "var(--sui-chart-1, #4f6bed)",
            "var(--sui-chart-2, #29a3a3)",
            "var(--sui-chart-3, #e0a300)",
            "var(--sui-chart-4, #c2410c)",
            "var(--sui-chart-5, #7c3aed)",
            "var(--sui-chart-6, #0891b2)");

    private static String color(int i) {
        return PALETTE.get(i % PALETTE.size());
    }

    private static final UiChart.ValueFormat NO_FORMAT = new UiChart.ValueFormat();

    /** The drawing for one chart node, or an empty-state paragraph. */
    public static String svg(UiChart node) {
        if (node == null) return "";
        var data = node.getData();
        List<String> labels = data == null || data.getLabels() == null ? List.of() : data.getLabels();
        List<UiChart.ChartData.Series> series = new ArrayList<>();
        if (data != null && data.getSeries() != null) {
            for (var s : data.getSeries()) {
                if (s != null && s.getValues() != null && !s.getValues().isEmpty()) series.add(s);
            }
        }
        if (series.isEmpty()) {
            return "<p class=\"sui-chart-empty\">No data.</p>";
        }
        UiChart.ChartType type = node.getChartType() == null ? UiChart.ChartType.BAR : node.getChartType();
        UiChart.ValueFormat format = node.getValueFormat() == null ? NO_FORMAT : node.getValueFormat();
        return switch (type) {
            case PIE   -> radial(series.get(0).getValues(), labels, false, format);
            case DONUT -> radial(series.get(0).getValues(), labels, true, format);
            default    -> cartesian(series, labels, type, Boolean.TRUE.equals(node.getStacked()),
                    !Boolean.FALSE.equals(node.getCrosshair()), format);
        };
    }

    // ── Numbers: must match extension.ts character for character ─────────────

    /**
     * Like JS toFixed(d), without a negative zero. Rounded from the double's
     * exact binary value, as JavaScript does: 1.005 is 1.00499999… and prints
     * "1.00" — String.format rounds its shortest decimal form, "1.005", to
     * "1.01".
     */
    private static String fixed(double v, int d) {
        var rounded = new java.math.BigDecimal(v == 0d ? 0d : v).setScale(d, java.math.RoundingMode.HALF_UP);
        // A zero keeps no sign in BigDecimal; JavaScript writes (-0.001).toFixed(2) as "-0.00".
        return (v < 0 && rounded.signum() == 0 ? "-" : "") + rounded.toPlainString();
    }

    private static String f(double v) {
        return fixed(v, 1);
    }

    /** Thousands separated by commas: "1234567.5" → "1,234,567.5". */
    private static String grouped(String text) {
        boolean neg = text.startsWith("-");
        String body = neg ? text.substring(1) : text;
        int dot = body.indexOf('.');
        String integer = dot < 0 ? body : body.substring(0, dot);
        String frac = dot < 0 ? "" : body.substring(dot);
        var out = new StringBuilder();
        for (int i = 0; i < integer.length(); i++) {
            if (i > 0 && (integer.length() - i) % 3 == 0) out.append(',');
            out.append(integer.charAt(i));
        }
        return (neg ? "-" : "") + out + frac;
    }

    /** Trailing zeros off a decimal, down to {@code keep} places. */
    private static String trimZeros(String text, int keep) {
        int dot = text.indexOf('.');
        if (dot < 0) return text;
        int end = text.length();
        while (end > dot + 1 + keep && text.charAt(end - 1) == '0') end--;
        if (end == dot + 1) end = dot;
        return text.substring(0, end);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** A value as a person reads it — see {@link UiChart.ValueFormat}. */
    static String formatValue(double v, UiChart.ValueFormat format) {
        String text;
        if (format.getDecimals() != null) {
            text = fixed(v, format.getDecimals());
        } else if (v == Math.floor(v) && !Double.isInfinite(v)) {
            text = fixed(v, 0);
        } else if (Math.abs(v) >= 1) {
            text = fixed(v, 2);
        } else {
            text = trimZeros(fixed(v, 4), 2);
        }
        return nz(format.getPrefix()) + grouped(text) + nz(format.getSuffix());
    }

    private static int stepDecimals(double step) {
        int d = 0;
        double s = step;
        while (d < 6 && Math.abs(s - Math.rint(s)) > 1e-9) {
            s *= 10;
            d++;
        }
        return d;
    }

    /** An axis tick, short: 250000 → "250k". The prefix stays, the suffix is the title's job. */
    static String formatTick(double v, double step, UiChart.ValueFormat format) {
        double a = Math.abs(v);
        String text;
        if (a >= 1e9) text = trimZeros(fixed(v / 1e9, 2), 0) + "B";
        else if (a >= 1e6) text = trimZeros(fixed(v / 1e6, 2), 0) + "M";
        else if (a >= 1e3) text = trimZeros(fixed(v / 1e3, 2), 0) + "k";
        else text = fixed(v, stepDecimals(step));
        return nz(format.getPrefix()) + text;
    }

    /** A round step for about four intervals: 1, 2, 2.5 or 5 times a power of ten, found as the TS finds it. */
    static double niceStep(double max) {
        double rough = max / 4;
        double mag = 1;
        while (mag * 10 <= rough) mag *= 10;
        while (mag > rough) mag /= 10;
        double norm = rough / mag;
        double m = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10;
        return m * mag;
    }

    /** A JSON array of strings, written the same way on both sides. */
    static String jsonStrings(List<String> values) {
        var out = new StringBuilder("[");
        for (int k = 0; k < values.size(); k++) {
            if (k > 0) out.append(',');
            out.append('"');
            String v = values.get(k);
            for (int i = 0; i < v.length(); i++) {
                char ch = v.charAt(i);
                if (ch == '"' || ch == '\\') out.append('\\').append(ch);
                else if (ch < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                else out.append(ch);
            }
            out.append('"');
        }
        return out.append(']').toString();
    }

    // ── Cartesian: BAR / LINE / AREA ────────────────────────────────────────

    private static final int W = 560, H = 240, PL = 48, PR = 12, PT = 14, PB = 30;
    private static final int IW = W - PL - PR, IH = H - PT - PB;

    private static double value(List<UiChart.ChartData.Series> series, int s, int i) {
        List<Number> values = series.get(s).getValues();
        Number v = i < values.size() ? values.get(i) : null;
        return Math.max(0, v == null ? 0 : v.doubleValue());
    }

    private static String cartesian(List<UiChart.ChartData.Series> series, List<String> labels,
                                     UiChart.ChartType type, boolean stackedWanted, boolean crosshair,
                                     UiChart.ValueFormat format) {
        boolean bar = type == UiChart.ChartType.BAR;
        boolean stacked = bar && stackedWanted && series.size() > 1;
        int n = 0;
        for (var s : series) n = Math.max(n, s.getValues().size());
        int k = series.size();

        double max = 0;
        for (int i = 0; i < n; i++) {
            if (stacked) {
                double sum = 0;
                for (int s = 0; s < k; s++) sum += value(series, s, i);
                max = Math.max(max, sum);
            } else {
                for (int s = 0; s < k; s++) max = Math.max(max, value(series, s, i));
            }
        }
        if (max <= 0) max = 1;
        double step = niceStep(max);
        int ticks = (int) Math.ceil(max / step - 1e-9);
        double top = ticks * step;
        double gap = (double) IW / n;
        final int count = n;
        java.util.function.DoubleUnaryOperator y = v -> PT + IH - IH * (v / top);
        java.util.function.IntToDoubleFunction cx = i -> bar
                ? PL + (i + 0.5) * gap
                : PL + (count == 1 ? IW / 2.0 : (double) IW * i / (count - 1));

        var sb = new StringBuilder("<svg class=\"sui-chart-svg sui-chart-cartesian\" viewBox=\"0 0 ")
                .append(W).append(' ').append(H).append("\" role=\"img\" aria-label=\"")
                .append(esc(type.name().toLowerCase(Locale.ROOT))).append(" chart\" data-series=\"");
        List<String> names = new ArrayList<>();
        for (int s = 0; s < k; s++) {
            String name = series.get(s).getName();
            names.add(name != null ? name : "Series " + (s + 1));
        }
        sb.append(esc(jsonStrings(names))).append("\">");

        for (int t = 0; t <= ticks; t++) {
            double v = t * step;
            double ty = y.applyAsDouble(v);
            if (t > 0) {
                sb.append("<line class=\"sui-chart-gridline\" x1=\"").append(PL).append("\" y1=\"").append(f(ty))
                  .append("\" x2=\"").append(PL + IW).append("\" y2=\"").append(f(ty))
                  .append("\" stroke=\"var(--sui-color-border, #e2e8f0)\"/>");
            }
            sb.append("<text x=\"").append(PL - 6).append("\" y=\"").append(f(ty + 3.5))
              .append("\" font-size=\"10\" text-anchor=\"end\" fill=\"var(--sui-color-text-muted, #64748b)\">")
              .append(esc(formatTick(v, step, format))).append("</text>");
        }
        sb.append("<line x1=\"").append(PL).append("\" y1=\"").append(PT + IH)
          .append("\" x2=\"").append(PL + IW).append("\" y2=\"").append(PT + IH)
          .append("\" stroke=\"var(--sui-color-border-strong, #94a3b8)\"/>");

        if (bar) {
            double group = gap * 0.7;
            for (int i = 0; i < n; i++) {
                double left = PL + i * gap + (gap - group) / 2;
                double base = 0;
                for (int s = 0; s < k; s++) {
                    double v = value(series, s, i);
                    double bw = stacked ? group : group / k;
                    double x = stacked ? left : left + s * bw;
                    double from = stacked ? base : 0;
                    double yTop = y.applyAsDouble(from + v);
                    double h = y.applyAsDouble(from) - yTop;
                    sb.append("<rect class=\"sui-chart-bar\" data-col=\"").append(i).append("\" x=\"").append(f(x))
                      .append("\" y=\"").append(f(yTop)).append("\" width=\"").append(f(bw))
                      .append("\" height=\"").append(f(h)).append("\" rx=\"2\" fill=\"").append(color(s))
                      .append("\"/>");
                    if (stacked) base += v;
                }
            }
        } else {
            for (int s = 0; s < k; s++) {
                var poly = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) poly.append(' ');
                    poly.append(f(cx.applyAsDouble(i))).append(',').append(f(y.applyAsDouble(value(series, s, i))));
                }
                if (type == UiChart.ChartType.AREA) {
                    sb.append("<polygon points=\"").append(PL).append(',').append(PT + IH).append(' ')
                      .append(poly).append(' ').append(PL + IW).append(',').append(PT + IH)
                      .append("\" fill=\"").append(color(s)).append("\" fill-opacity=\"0.15\"/>");
                }
                sb.append("<polyline points=\"").append(poly).append("\" fill=\"none\" stroke=\"")
                  .append(color(s)).append("\" stroke-width=\"2\"/>");
                for (int i = 0; i < n; i++) {
                    sb.append("<circle class=\"sui-chart-dot\" data-col=\"").append(i).append("\" cx=\"")
                      .append(f(cx.applyAsDouble(i))).append("\" cy=\"").append(f(y.applyAsDouble(value(series, s, i))))
                      .append("\" r=\"3\" fill=\"").append(color(s)).append("\"/>");
                }
            }
        }

        // Every label only when they fit; a month of days shows every few.
        int every = Math.max(1, (int) Math.ceil((double) n / Math.max(1, IW / 70)));
        for (int i = 0; i < labels.size() && i < n; i++) {
            if (i % every != 0) continue;
            sb.append("<text x=\"").append(f(cx.applyAsDouble(i))).append("\" y=\"").append(H - 10)
              .append("\" font-size=\"10\" text-anchor=\"middle\" fill=\"var(--sui-color-text-muted, #64748b)\">")
              .append(esc(labels.get(i))).append("</text>");
        }

        if (crosshair) {
            sb.append("<line class=\"sui-chart-cursor\" x1=\"").append(PL).append("\" y1=\"").append(PT)
              .append("\" x2=\"").append(PL).append("\" y2=\"").append(PT + IH).append("\"/>");
        }

        sb.append("<g class=\"sui-chart-cols\">");
        for (int i = 0; i < n; i++) {
            String label = label(labels, i);
            List<String> shown = new ArrayList<>();
            double total = 0;
            for (int s = 0; s < k; s++) {
                shown.add(formatValue(value(series, s, i), format));
                total += value(series, s, i);
            }
            String title;
            if (k == 1) {
                title = label + ": " + shown.get(0);
            } else {
                var parts = new ArrayList<String>();
                for (int s = 0; s < k; s++) parts.add(names.get(s) + " " + shown.get(s));
                title = label + ": " + String.join(", ", parts);
            }
            double band = bar ? gap : (n == 1 ? IW : (double) IW / (n - 1));
            double left = bar ? PL + i * gap : cx.applyAsDouble(i) - band / 2;
            sb.append("<rect class=\"sui-chart-col\" data-col=\"").append(i).append("\" data-x=\"")
              .append(f(cx.applyAsDouble(i))).append("\" data-label=\"").append(esc(label))
              .append("\" data-values=\"").append(esc(jsonStrings(shown))).append('"');
            if (stacked) sb.append(" data-total=\"").append(esc(formatValue(total, format))).append('"');
            sb.append(" x=\"").append(f(left)).append("\" y=\"").append(PT).append("\" width=\"").append(f(band))
              .append("\" height=\"").append(IH).append("\" fill=\"transparent\"><title>").append(esc(title))
              .append("</title></rect>");
        }
        sb.append("</g></svg>");

        if (k > 1) {
            sb.append("<ul class=\"sui-chart-legend sui-chart-legend--row\">");
            for (int s = 0; s < k; s++) {
                sb.append("<li><span class=\"sui-chart-swatch\" style=\"background:").append(color(s))
                  .append("\"></span>").append(esc(names.get(s))).append("</li>");
            }
            sb.append("</ul>");
        }
        return sb.toString();
    }

    // ── Radial: PIE / DONUT ─────────────────────────────────────────────────

    private static String radial(List<Number> raw, List<String> labels, boolean donut, UiChart.ValueFormat format) {
        final int cx = 85, cy = 85;
        final int r = donut ? 60 : 42;
        // A pie is a donut whose stroke is wide enough to close the hole.
        final int sw = donut ? 20 : 84;
        final double c = 2 * Math.PI * r;
        double[] values = new double[raw.size()];
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            Number v = raw.get(i);
            values[i] = Math.max(0, v == null ? 0 : v.doubleValue());
            sum += values[i];
        }
        final double total = sum == 0 ? 1 : sum;

        var segs = new StringBuilder();
        double offset = 0;
        for (int i = 0; i < values.length; i++) {
            double len = values[i] / total * c;
            String shown = formatValue(values[i], format);
            String pct = fixed(values[i] / total * 100, 0) + "%";
            segs.append("<circle class=\"sui-chart-seg\" data-col=\"").append(i).append("\" data-label=\"")
                .append(esc(label(labels, i))).append("\" data-values=\"").append(esc(jsonStrings(List.of(shown))))
                .append("\" data-pct=\"").append(pct).append("\" cx=\"").append(cx).append("\" cy=\"").append(cy)
                .append("\" r=\"").append(r).append("\" fill=\"none\" stroke=\"").append(color(i))
                .append("\" stroke-width=\"").append(sw)
                .append("\" stroke-dasharray=\"").append(fixed(len, 2)).append(' ').append(fixed(c - len, 2))
                .append("\" stroke-dashoffset=\"").append(fixed(-offset, 2))
                .append("\" transform=\"rotate(-90 ").append(cx).append(' ').append(cy).append(")\">")
                .append("<title>").append(esc(label(labels, i))).append(": ").append(esc(shown))
                .append(" (").append(pct).append(")</title></circle>");
            offset += len;
        }

        var legend = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            double v = i < values.length ? values[i] : 0;
            legend.append("<li data-col=\"").append(i).append("\"><span class=\"sui-chart-swatch\" style=\"background:")
                  .append(color(i)).append("\"></span><span class=\"sui-chart-legend-name\">")
                  .append(esc(labels.get(i))).append("</span><span class=\"sui-chart-legend-value\">")
                  .append(esc(formatValue(v, format))).append(" · ").append(fixed(v / total * 100, 0))
                  .append("%</span></li>");
        }

        return "<div class=\"sui-chart-radial\">"
                + "<svg class=\"sui-chart-svg\" viewBox=\"0 0 170 170\" role=\"img\" aria-label=\""
                + (donut ? "donut" : "pie") + " chart\">" + segs + "</svg>"
                + "<ul class=\"sui-chart-legend\">" + legend + "</ul>"
                + "</div>";
    }

    private static String label(List<String> labels, int i) {
        return i < labels.size() && labels.get(i) != null ? labels.get(i) : "";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
