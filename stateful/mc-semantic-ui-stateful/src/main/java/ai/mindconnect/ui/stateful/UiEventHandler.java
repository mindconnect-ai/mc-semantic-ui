package ai.mindconnect.ui.stateful;

/**
 * A server-side listener. Bound to a node and an event during
 * {@link SuiView#render}, called with the {@link UiEvent} when the client
 * fires it. It changes the view's state (or calls the view's intent methods —
 * toast, navigate) and returns nothing: the framework re-renders and diffs.
 *
 * <p>Handlers may throw. A checked exception ends up as a failed request and
 * an error toast, the state is left as it was before the event.
 */
@FunctionalInterface
public interface UiEventHandler {

    void handle(UiEvent event) throws Exception;
}
