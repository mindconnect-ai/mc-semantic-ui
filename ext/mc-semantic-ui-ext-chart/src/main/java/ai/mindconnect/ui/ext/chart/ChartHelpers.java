package ai.mindconnect.ui.ext.chart;

import ai.mindconnect.ui.ssr.SuiHelperContributor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;

/**
 * Supplies the one helper this module's template calls. Registered two ways so
 * the same JAR works with and without Spring: via {@code META-INF/services}
 * (ServiceLoader) and as a Spring bean from {@link UiChartAutoConfiguration}.
 *
 * <p>Both paths end in the renderer, and registering twice is harmless — the
 * second registration simply replaces the first with an identical helper.
 */
public final class ChartHelpers implements SuiHelperContributor {

    @Override
    public void contribute(Handlebars handlebars, ObjectMapper mapper) {
        // {{{chartSvg this}}} — the drawing, straight from ChartPainter.
        handlebars.registerHelper("chartSvg", (ctx, opts) -> {
            if (!(ctx instanceof UiChart chart)) return "";
            return new Handlebars.SafeString(ChartPainter.svg(chart));
        });
    }
}
