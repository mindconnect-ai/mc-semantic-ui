package ai.mindconnect.ui.javafx;

/**
 * One live SSE stream the bus is reading — the JavaFX twin of the TS
 * {@code StreamHandle}.
 *
 * <p>A stream outlives the tree it started from. The user navigates, the
 * renderer remounts, and the reader keeps going; the handle is what survives
 * that, so a page can find its stream again by {@link #channelId()} instead of
 * opening a second one.
 */
public final class FxStreamHandle {

    /** Where a stream is in its life. */
    public enum State { RUNNING, COMPLETED, ERRORED }

    private final String channelId;
    private final String label;
    private final String returnHref;
    private final Runnable abort;
    private volatile State state = State.RUNNING;
    private volatile long lastSeq;

    FxStreamHandle(String channelId, String label, String returnHref, Runnable abort) {
        this.channelId = channelId;
        this.label = label;
        this.returnHref = returnHref;
        this.abort = abort;
    }

    /** The server's id for this channel, from the {@code Sui-Stream-Channel} header. */
    public String channelId() {
        return channelId;
    }

    /** Human-readable name for a status indicator; {@code "Agent"} when the server names none. */
    public String label() {
        return label;
    }

    /** Where to send a user who wants to get back to this stream's page. */
    public String returnHref() {
        return returnHref;
    }

    public State state() {
        return state;
    }

    /**
     * The highest SSE event id seen. The server publishes the channel's
     * monotonic sequence as the event id, so a reconnect can pass this as
     * {@code lastSeq} and skip what already arrived.
     */
    public long lastSeq() {
        return lastSeq;
    }

    /** Stops reading. The server's own end of the stream closes on its next write. */
    public void abort() {
        abort.run();
    }

    void seen(long seq) {
        if (seq > lastSeq) lastSeq = seq;
    }

    void state(State next) {
        this.state = next;
    }

    @Override
    public String toString() {
        return "FxStreamHandle[" + channelId + " " + state + " seq=" + lastSeq + "]";
    }
}
