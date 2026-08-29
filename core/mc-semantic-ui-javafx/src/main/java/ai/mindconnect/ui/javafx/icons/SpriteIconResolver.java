package ai.mindconnect.ui.javafx.icons;


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The default {@link FxIconResolver}: reads the very sprite the browser loads,
 * {@code icons.svg}, and rebuilds its symbols as JavaFX shapes.
 *
 * <p>That the desktop and the browser draw from one file is the point — an
 * icon token means the same glyph in all three renderers, and adding an icon
 * to the sprite lights it up everywhere at once.
 *
 * <p>The sprite is ~670 KB of markup, so it is read once, lazily, into a map of
 * symbol id to its inner markup. Only the requested symbol is ever parsed into
 * shapes, and each call builds fresh ones — a JavaFX node has a single parent,
 * so cached shapes could not be reused across the tree anyway.
 */
public class SpriteIconResolver implements FxIconResolver {

    /** Where core's jar puts the sprite; the same file Spring serves at /sui/icons.svg. */
    public static final String DEFAULT_SPRITE = "/META-INF/resources/sui/icons.svg";

    // Pulled apart with a scan rather than a DOM: holding 2000+ parsed symbols
    // would cost megabytes to answer a handful of lookups.
    private static final Pattern SYMBOL =
            Pattern.compile("<symbol\\s+([^>]*?)>(.*?)</symbol>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("\\bid=\"([^\"]+)\"");

    private final String spritePath;
    private volatile Map<String, String> symbols;

    public SpriteIconResolver() {
        this(DEFAULT_SPRITE);
    }

    public SpriteIconResolver(String spritePath) {
        this.spritePath = spritePath;
    }

    @Override
    public SuiFxIcon resolve(String name) {
        if (name == null || name.isBlank()) return null;
        var markup = symbols().get(name.trim());
        if (markup == null) return null;
        var document = SvgDocument.parse(markup);
        return document.isEmpty() ? null : new SuiFxIcon(name, document);
    }

    /** The tokens this sprite carries — useful for a picker, and for tests. */
    public java.util.Set<String> names() {
        return java.util.Collections.unmodifiableSet(symbols().keySet());
    }

    private Map<String, String> symbols() {
        var loaded = symbols;
        if (loaded != null) return loaded;
        synchronized (this) {
            if (symbols == null) symbols = load();
            return symbols;
        }
    }

    private Map<String, String> load() {
        try (InputStream in = SpriteIconResolver.class.getResourceAsStream(spritePath)) {
            if (in == null) return Map.of();
            var svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> found = new HashMap<>();
            Matcher m = SYMBOL.matcher(svg);
            while (m.find()) {
                var attributes = m.group(1);
                var id = ID.matcher(attributes);
                if (!id.find()) continue;
                // Stored as a document in its own right: a symbol states its
                // viewBox and stroke width on its own tag, and dropping those
                // on the way in would mean guessing them on the way out.
                found.put(id.group(1), "<svg " + attributes + ">" + m.group(2) + "</svg>");
            }
            return Map.copyOf(found);
        } catch (IOException e) {
            // A missing or unreadable sprite costs glyphs, not the window.
            return Map.of();
        }
    }
}
