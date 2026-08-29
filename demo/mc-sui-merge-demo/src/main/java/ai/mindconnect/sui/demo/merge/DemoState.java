package ai.mindconnect.sui.demo.merge;

import ai.mindconnect.ui.model.UiNode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * What the demo remembers between clicks. One instance for the whole app —
 * this is a page to poke at, not a product, and a session per visitor would be
 * ceremony around three fields.
 *
 * <p>Shared, though, which means shared across request threads: a double-click
 * or a second tab is enough to run two of these methods at once, and an
 * ArrayDeque torn between {@code addFirst} and {@code removeLast} comes back
 * with a corrupt list rather than an old one. So every accessor is
 * synchronized. At this traffic it costs nothing, and being unsynchronised
 * here was an oversight rather than a decision.
 */
@Component
public class DemoState {

    /** How many exchanges the wire log keeps. Enough to compare, short enough to read. */
    private static final int LOG_DEPTH = 6;

    /** {@code null} means visible; the two enum values are the two ways to hide. */
    private UiNode.Display advanced;
    private boolean notifications = true;

    private final Deque<Exchange> wire = new ArrayDeque<>();

    /**
     * One thing the server sent, and the {@code REPLACE} that would have had
     * the same effect on screen — which is the comparison this demo exists to
     * make.
     *
     * <p>The byte counts are of the compact form, because that is what a
     * server sends; the strings are pretty-printed, because that is what a
     * person reads. Counting the pretty version would flatter the shorter
     * operation, and the point is not to win by formatting.
     *
     * @param what         the click, in words
     * @param sent         the JSON actually sent, laid out to be read
     * @param sentBytes    what it weighs on the wire
     * @param instead      the JSON that would have gone otherwise
     * @param insteadBytes what that weighs on the wire
     */
    public record Exchange(String what, String sent, int sentBytes,
                           String instead, int insteadBytes) {
    }

    public synchronized UiNode.Display advanced() {
        return advanced;
    }

    public synchronized void setAdvanced(UiNode.Display display) {
        this.advanced = display;
    }

    public synchronized boolean notifications() {
        return notifications;
    }

    public synchronized void toggleNotifications() {
        this.notifications = !this.notifications;
    }

    /** Newest first — the last thing you clicked is the one you want to read. */
    public synchronized List<Exchange> wire() {
        return List.copyOf(wire);
    }

    public synchronized void record(Exchange exchange) {
        wire.addFirst(exchange);
        while (wire.size() > LOG_DEPTH) wire.removeLast();
    }

    public synchronized void reset() {
        advanced = null;
        notifications = true;
        wire.clear();
    }
}
