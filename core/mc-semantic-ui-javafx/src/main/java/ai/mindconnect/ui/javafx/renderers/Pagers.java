package ai.mindconnect.ui.javafx.renderers;

/**
 * The page arithmetic, in one place because both pagers had it wrong the same
 * way.
 *
 * <p>{@code page} is <b>one-based</b>. The SPA has always treated it so —
 * {@code p.page <= 1} is its first-page test and it prints {@code p.page}
 * unchanged — and a server sending {@code page: 1, size: 10, total: 16} means
 * the first ten of sixteen.
 *
 * <p>These renderers assumed zero. Every symptom followed from that one
 * mistake: the label read "2 / 2" on the first page, Previous was enabled
 * there, and pressing it asked for page 0, which a one-based server has no
 * answer for.
 */
final class Pagers {

    private Pagers() {
    }

    /** How many pages there are; at least one, so an empty list still reads "1 / 1". */
    static int pageCount(int size, long total) {
        if (size <= 0) return 1;
        return (int) Math.max(1, Math.ceil((double) total / size));
    }

    static boolean isFirst(int page) {
        return page <= 1;
    }

    static boolean isLast(int page, int size, long total) {
        return page >= pageCount(size, total);
    }

    /** "2 / 5  (47 items)" — the page as the server numbered it, not one more. */
    static String label(int page, int size, long total, String noun) {
        return page + " / " + pageCount(size, total) + "  (" + total + " " + noun + ")";
    }
}
