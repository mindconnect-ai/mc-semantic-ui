package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiProgress;

import static ai.mindconnect.ui.ssr.SuiHandlebarsHelpers.escapeHtml;

/**
 * SSR mirror of {@code renderers/progress.ts}. Kept as a single render method
 * (rather than a branchy {@code .hbs} template) because the circular variant
 * needs SVG plus percentage / dash-offset arithmetic that Handlebars expresses
 * awkwardly — same reasoning as {@link IconRenderer}. The bundled
 * {@code progress.hbs} is a one-liner delegating here via the {@code progress}
 * helper, so the SSR output is byte-compatible with the SPA renderer.
 */
public final class ProgressRenderer {

    private ProgressRenderer() {}

    public static String render(UiProgress p) {
        if (p == null) return "";
        double max = (p.getMax() != null && p.getMax() > 0) ? p.getMax() : 100d;
        boolean indeterminate = p.getValue() == null;
        int pct = indeterminate
                ? 0
                : (int) Math.round(Math.max(0, Math.min(100, (p.getValue() / max) * 100)));
        boolean circle = p.getVariant() == UiProgress.Variant.CIRCLE;
        String variant = circle ? "circle" : "bar";
        String status = (p.getStatus() == null ? UiProgress.Status.NORMAL : p.getStatus())
                .name().toLowerCase();
        boolean showValue = !Boolean.FALSE.equals(p.getShowValue()) && !indeterminate;

        String rootCls = "sui-progress sui-progress--" + variant + " sui-progress--" + status
                + (indeterminate ? " sui-progress--indeterminate" : "")
                + (p.getCssClass() != null ? " " + escapeHtml(p.getCssClass()) : "");
        String id = p.getId() != null ? " id=\"" + escapeHtml(p.getId()) + "\"" : "";
        String aria = indeterminate
                ? "role=\"progressbar\" aria-busy=\"true\""
                : "role=\"progressbar\" aria-valuenow=\"" + pct + "\" aria-valuemin=\"0\" aria-valuemax=\"100\"";
        String titleAttr = p.getTitle() != null ? " aria-label=\"" + escapeHtml(p.getTitle()) + "\"" : "";

        if (circle) {
            int dashoffset = indeterminate ? 75 : 100 - pct;
            String text = showValue
                    ? "<text class=\"sui-progress-ring-text\" x=\"18\" y=\"20.5\" text-anchor=\"middle\">" + pct + "%</text>"
                    : "";
            return "<div" + id + " class=\"" + rootCls + "\" " + aria + titleAttr + ">"
                    + "<svg class=\"sui-progress-ring\" viewBox=\"0 0 36 36\" width=\"40\" height=\"40\">"
                    + "<circle class=\"sui-progress-ring-track\" cx=\"18\" cy=\"18\" r=\"15.9155\" fill=\"none\" stroke-width=\"3\"></circle>"
                    + "<circle class=\"sui-progress-ring-fill\" cx=\"18\" cy=\"18\" r=\"15.9155\" fill=\"none\" stroke-width=\"3\" "
                    + "transform=\"rotate(-90 18 18)\" stroke-dasharray=\"100\" stroke-dashoffset=\"" + dashoffset + "\"></circle>"
                    + text + "</svg></div>";
        }

        String fillStyle = indeterminate ? "" : " style=\"width:" + pct + "%\"";
        String text = showValue
                ? "<span class=\"sui-progress-text\">" + pct + "%</span>"
                : "";
        return "<div" + id + " class=\"" + rootCls + "\" " + aria + titleAttr + ">"
                + "<div class=\"sui-progress-track\"><div class=\"sui-progress-fill\"" + fillStyle + "></div></div>"
                + text + "</div>";
    }
}
