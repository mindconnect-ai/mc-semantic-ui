package ai.mindconnect.ui.html;

import java.util.regex.Pattern;

/**
 * A colour a node carries for its style attribute — a lane's accent, an
 * event's — checked before it is written there. Only colour syntax passes: a
 * hex value, a name, {@code rgb()}/{@code rgba()}/{@code hsl()}/{@code hsla()}
 * with numbers in them, or {@code var(--name)}. Anything else, in particular
 * a value with a semicolon that would add declarations of its own
 * ({@code red;background:url(…)}), is dropped.
 *
 * <p>The extensions' browser painters apply the same pattern; keep them in
 * step.
 */
public final class CssColor {

    private CssColor() {}

    /** The one pattern — mirrored as SAFE_COLOR in the extensions' TypeScript. */
    public static final Pattern SAFE = Pattern.compile(
            "^(?:#[0-9a-fA-F]{3,8}|[a-zA-Z]{1,32}|(?:rgb|rgba|hsl|hsla)\\([0-9.,% /+-]{1,64}\\)|var\\(--[a-zA-Z0-9_-]{1,64}\\))$");

    /** The colour if it is plain colour syntax, else null. */
    public static String orNull(String color) {
        return color != null && SAFE.matcher(color).matches() ? color : null;
    }
}
