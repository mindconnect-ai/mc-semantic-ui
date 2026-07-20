package ai.mindconnect.ui.ext.chart;


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
 * six numbers. The tests pin both sides to the same output.
 *
 * <p>Everything is plain SVG with {@code <title>} tooltips, so a chart rendered
 * this way needs <b>no JavaScript at all</b>.
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

    /** The drawing for one chart node, or an empty-state paragraph. */
    public static String svg(UiChart node) {
        if (node == null) return "";
        var data = node.getData();
        List<String> labels = data == null || data.getLabels() == null ? List.of() : data.getLabels();
        List<Number> values = firstSeriesValues(data);
        if (values.isEmpty()) {
            return "<p class=\"sui-chart-empty\">No data.</p>";
        }
        UiChart.ChartType type = node.getChartType() == null ? UiChart.ChartType.BAR : node.getChartType();
        return switch (type) {
            case PIE   -> radial(values, labels, false);
            case DONUT -> radial(values, labels, true);
            default    -> cartesian(values, labels, type);
        };
    }

    private static List<Number> firstSeriesValues(UiChart.ChartData data) {
        if (data == null || data.getSeries() == null || data.getSeries().isEmpty()) return List.of();
        var values = data.getSeries().get(0).getValues();
        return values == null ? List.of() : values;
    }

    // ── Cartesian: BAR / LINE / AREA ────────────────────────────────────────

    private static String cartesian(List<Number> values, List<String> labels, UiChart.ChartType type) {
        final int W = 320, H = 170, P = 30;
        final int iw = W - 2 * P, ih = H - 2 * P;
        double max = 1;
        for (Number v : values) max = Math.max(max, v.doubleValue());
        int n = values.size();

        var sb = new StringBuilder();
        sb.append("<line x1=\"").append(P).append("\" y1=\"").append(P + ih)
          .append("\" x2=\"").append(P + iw).append("\" y2=\"").append(P + ih)
          .append("\" stroke=\"var(--sui-color-border-strong, #94a3b8)\"/>");

        if (type == UiChart.ChartType.BAR) {
            double gap = (double) iw / n, bw = gap * 0.6;
            for (int i = 0; i < n; i++) {
                double v = values.get(i).doubleValue();
                double bh = ih * (v / max);
                double x = P + i * gap + (gap - bw) / 2;
                double y = P + ih - bh;
                sb.append("<rect x=\"").append(f(x)).append("\" y=\"").append(f(y))
                  .append("\" width=\"").append(f(bw)).append("\" height=\"").append(f(bh))
                  .append("\" rx=\"2\" fill=\"").append(PALETTE.get(0)).append("\">")
                  .append("<title>").append(esc(label(labels, i))).append(": ").append(num(values.get(i)))
                  .append("</title></rect>");
            }
        } else {
            var pts = new StringBuilder();
            var dots = new StringBuilder();
            for (int i = 0; i < n; i++) {
                double v = values.get(i).doubleValue();
                double x = P + (n == 1 ? iw / 2.0 : (double) iw * i / (n - 1));
                double y = P + ih - ih * (v / max);
                if (i > 0) pts.append(' ');
                pts.append(f(x)).append(',').append(f(y));
                dots.append("<circle cx=\"").append(f(x)).append("\" cy=\"").append(f(y))
                    .append("\" r=\"3\" fill=\"").append(PALETTE.get(0)).append("\">")
                    .append("<title>").append(esc(label(labels, i))).append(": ").append(num(values.get(i)))
                    .append("</title></circle>");
            }
            if (type == UiChart.ChartType.AREA) {
                sb.append("<polygon points=\"").append(P).append(',').append(P + ih).append(' ')
                  .append(pts).append(' ').append(P + iw).append(',').append(P + ih)
                  .append("\" fill=\"var(--sui-color-primary-soft, #dbeafe)\"/>");
            }
            sb.append("<polyline points=\"").append(pts).append("\" fill=\"none\" stroke=\"")
              .append(PALETTE.get(0)).append("\" stroke-width=\"2\"/>").append(dots);
        }

        for (int i = 0; i < labels.size(); i++) {
            double x = P + (type == UiChart.ChartType.BAR
                    ? (i + 0.5) * ((double) iw / n)
                    : (n == 1 ? iw / 2.0 : (double) iw * i / (n - 1)));
            sb.append("<text x=\"").append(f(x)).append("\" y=\"").append(H - 8)
              .append("\" font-size=\"10\" text-anchor=\"middle\" ")
              .append("fill=\"var(--sui-color-text-muted, #64748b)\">")
              .append(esc(labels.get(i))).append("</text>");
        }

        return "<svg class=\"sui-chart-svg\" viewBox=\"0 0 " + W + " " + H + "\" role=\"img\" aria-label=\""
                + type.name().toLowerCase(Locale.ROOT) + " chart\">" + sb + "</svg>";
    }

    // ── Radial: PIE / DONUT ─────────────────────────────────────────────────

    private static String radial(List<Number> values, List<String> labels, boolean donut) {
        final int cx = 85, cy = 85;
        final int r = donut ? 60 : 42;
        // A pie is a donut whose stroke is wide enough to close the hole.
        final int sw = donut ? 20 : 84;
        final double c = 2 * Math.PI * r;
        double total = 0;
        for (Number v : values) total += v.doubleValue();
        if (total == 0) total = 1;

        var segs = new StringBuilder();
        double offset = 0;
        for (int i = 0; i < values.size(); i++) {
            double len = values.get(i).doubleValue() / total * c;
            segs.append("<circle cx=\"").append(cx).append("\" cy=\"").append(cy)
                .append("\" r=\"").append(r).append("\" fill=\"none\" stroke=\"")
                .append(PALETTE.get(i % PALETTE.size())).append("\" stroke-width=\"").append(sw)
                .append("\" stroke-dasharray=\"").append(f2(len)).append(' ').append(f2(c - len))
                .append("\" stroke-dashoffset=\"").append(f2(-offset))
                .append("\" transform=\"rotate(-90 ").append(cx).append(' ').append(cy).append(")\">")
                .append("<title>").append(esc(label(labels, i))).append(": ").append(num(values.get(i)))
                .append("</title></circle>");
            offset += len;
        }

        var legend = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            legend.append("<li><span class=\"sui-chart-swatch\" style=\"background:")
                  .append(PALETTE.get(i % PALETTE.size())).append("\"></span>")
                  .append(esc(labels.get(i))).append("</li>");
        }

        return "<div class=\"sui-chart-radial\">"
                + "<svg class=\"sui-chart-svg\" viewBox=\"0 0 170 170\" role=\"img\" aria-label=\""
                + (donut ? "donut" : "pie") + " chart\">" + segs + "</svg>"
                + "<ul class=\"sui-chart-legend\">" + legend + "</ul>"
                + "</div>";
    }

    // ── Formatting: must match JS number output exactly ──────────────────────

    /** One decimal, like JS toFixed(1). */
    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", zero(v));
    }

    /** Two decimals, like JS toFixed(2). */
    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", zero(v));
    }

    /**
     * Collapses negative zero. The first pie segment starts at offset -0.0,
     * which Java prints as "-0.00" and JavaScript as "0.00" — the drawings are
     * identical, the strings are not, and the parity check compares strings.
     */
    private static double zero(double v) {
        return v == 0d ? 0d : v;
    }

    /**
     * Renders a value the way JavaScript would print it: whole numbers without
     * a trailing ".0". Without this the SSR tooltip reads "12.0" where the
     * browser reads "12" — a small divergence, but the parity tests compare
     * strings and users compare screenshots.
     */
    private static String num(Number n) {
        double d = n.doubleValue();
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
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
