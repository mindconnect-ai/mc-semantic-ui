package ai.mindconnect.ui.ssr;

import java.util.regex.Pattern;

/**
 * Server-side icon rendering — the SSR mirror of {@code renderers/icon.ts}.
 * Emits the same {@code <svg class="sui-icon"><use href="…/icons.svg#token">}
 * markup so an SSR page and its hydrated SPA counterpart are byte-compatible.
 *
 * <p><b>Swappable.</b> UiNodes only carry a token; what it renders to is
 * decided here. The default resolves against the curated sprite served at
 * {@link #getSpriteUrl()} (default {@code /sui/icons.svg}). Host apps that
 * serve the sprite elsewhere call {@link #setSpriteUrl(String)} once at
 * startup; apps that want an entirely different scheme (inline SVG, an icon
 * font) install their own {@link Resolver} via {@link #setResolver(Resolver)}.
 *
 * <p>A token that matches the sprite-id shape ({@code ^[a-z][a-z0-9-]*$})
 * resolves to the sprite; anything else (an emoji, arbitrary text) is emitted
 * verbatim as a literal glyph — the migration path for legacy
 * {@code icon: "📁"} data.
 */
public final class IconRenderer {

    /** Turns an icon token + optional class/title/id into HTML. Swappable. */
    @FunctionalInterface
    public interface Resolver {
        String render(String name, String cssClass, String title, String id);
    }

    private static final Pattern TOKEN = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static volatile String spriteUrl = "/sui/icons.svg";
    private static volatile Resolver resolver = IconRenderer::spriteMarkup;

    private IconRenderer() {}

    public static String getSpriteUrl() { return spriteUrl; }

    /** Point the default sprite resolver at a different sprite URL. */
    public static void setSpriteUrl(String url) { spriteUrl = url; }

    /** Replace the resolver entirely (inline SVG, icon font, …). */
    public static void setResolver(Resolver r) { resolver = r; }

    /**
     * Renders an icon token to HTML. Returns {@code ""} for a null/blank
     * token so callers can concatenate unconditionally. {@code id} is set for
     * a standalone {@link ai.mindconnect.ui.model.UiIcon} (patch-addressable),
     * omitted for decorative leading icons.
     */
    public static String render(String name, String cssClass, String title, String id) {
        if (name == null || name.isEmpty()) return "";
        String idAttr = id != null && !id.isEmpty() ? " id=\"" + esc(id) + "\"" : "";
        if (!TOKEN.matcher(name).matches()) {
            // Literal glyph (emoji / text) — passed through verbatim.
            String cls = cssClass != null && !cssClass.isEmpty()
                    ? " class=\"" + esc(cssClass) + "\"" : "";
            return "<span" + idAttr + cls + " aria-hidden=\"true\">" + esc(name) + "</span>";
        }
        return resolver.render(name, cssClass, title, id);
    }

    /** Convenience for the common decorative leading-icon case. */
    public static String render(String name, String cssClass, String title) {
        return render(name, cssClass, title, null);
    }

    /** Convenience for the common decorative case (no class, no title, no id). */
    public static String render(String name) { return render(name, null, null, null); }

    private static String spriteMarkup(String name, String cssClass, String title, String id) {
        String cls = "sui-icon" + (cssClass != null && !cssClass.isEmpty() ? " " + esc(cssClass) : "");
        String idAttr = id != null && !id.isEmpty() ? " id=\"" + esc(id) + "\"" : "";
        String a11y = title != null && !title.isEmpty()
                ? "role=\"img\" aria-label=\"" + esc(title) + "\""
                : "aria-hidden=\"true\"";
        String titleEl = title != null && !title.isEmpty()
                ? "<title>" + esc(title) + "</title>" : "";
        return "<svg" + idAttr + " class=\"" + cls + "\" " + a11y + ">" + titleEl
                + "<use href=\"" + esc(spriteUrl) + "#" + esc(name) + "\"></use></svg>";
    }

    private static String esc(Object v) { return SuiHandlebarsHelpers.escapeHtml(v); }
}
