package ai.mindconnect.ui.javafx;

/**
 * Whether a string from the model counts as present.
 *
 * <p>This exists because of one small difference that shows up all over the
 * screen. The TS renderers write {@code node.title ? … : ""}, and in JavaScript
 * an empty string is falsy — so a server sending {@code "title": ""} gets no
 * heading at all. A Java {@code != null} check does not agree: it renders the
 * heading, empty, and the layout keeps the room for it.
 *
 * <p>Real servers do send empty strings — a list whose title belongs to its
 * parent, an item whose label lives in its collapse summary — and each one
 * became a blank strip on the desktop that the browser never showed. The two
 * renderers are supposed to produce the same screen, so they have to agree on
 * what "no title" means.
 */
public final class SuiFxText {

    private SuiFxText() {
    }

    /** JavaScript truthiness for a string: {@code null} and blank are both absent. */
    public static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The first present value, or {@code null} — the counterpart of the TS
     * renderers' {@code node.label ?? node.title} chains, which fall through an
     * empty string rather than stopping at it.
     */
    public static String first(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (present(value)) return value;
        }
        return null;
    }
}
