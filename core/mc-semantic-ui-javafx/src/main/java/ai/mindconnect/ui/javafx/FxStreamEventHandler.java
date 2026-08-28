package ai.mindconnect.ui.javafx;

/**
 * Handles one SSE event arriving on a {@code STREAM} behaviour — the JavaFX
 * twin of the TS {@code StreamEventHandler}.
 *
 * <p>Registered per event name via
 * {@link SuiFxEventBus#onStreamEvent(String, FxStreamEventHandler)}. The bus
 * ships one built-in, for {@code patch}; anything else an app's protocol
 * defines is its own to register.
 *
 * <p>Called on the FX thread, so an implementation may touch the scene graph
 * directly.
 */
@FunctionalInterface
public interface FxStreamEventHandler {

    /**
     * @param data   the event's {@code data:} payload, with multi-line data
     *               already joined by newlines
     * @param handle the stream it arrived on
     */
    void handle(String data, FxStreamHandle handle);
}
