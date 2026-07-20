package ai.mindconnect.ui.ssr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;

/**
 * Extension point for adding Handlebars helpers to the server renderer.
 *
 * <p>An extension module that wants to render server-side needs two things: a
 * template for its node type, and the helpers that template calls. The first
 * already works — a {@code templates/sui/<type>.hbs} on the classpath shadows
 * the core's copy. This interface supplies the second, because the maths a
 * painter needs (SVG geometry, scales, arcs) cannot live in a logic-less
 * template.
 *
 * <p>Implementations are discovered two ways, so the same JAR works with and
 * without Spring:
 * <ul>
 *   <li><b>ServiceLoader</b> — declare it in
 *       {@code META-INF/services/ai.mindconnect.ui.ssr.SuiHelperContributor}.</li>
 *   <li><b>Spring</b> — publish it as a bean; the auto-configuration passes
 *       every bean it finds to the renderer.</li>
 * </ul>
 * Both are applied after the core helpers, so a contributor can also replace a
 * core helper by registering the same name — the last registration wins.
 *
 * <pre>{@code
 * public final class ChartHelpers implements SuiHelperContributor {
 *     @Override
 *     public void contribute(Handlebars hb, ObjectMapper mapper) {
 *         hb.registerHelper("chartSvg", (ctx, opts) -> ChartPainter.svg(ctx));
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface SuiHelperContributor {

    /**
     * Registers helpers on the renderer's Handlebars instance.
     *
     * @param handlebars the engine the renderer will use
     * @param mapper     the renderer's ObjectMapper, for helpers that need to
     *                   serialise a payload into an attribute
     */
    void contribute(Handlebars handlebars, ObjectMapper mapper);
}
