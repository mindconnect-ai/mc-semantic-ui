package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiToast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A server-side stateful view: state that lives between requests, a
 * {@link #render(Object)} that turns it into a {@link UiNode} tree, and Java
 * listeners bound to nodes while rendering.
 *
 * <pre>{@code
 * @SuiRoute("/counter")
 * public class CounterView extends SuiView<CounterView.State> {
 *
 *     public static class State { int count; }
 *
 *     @Override protected State initialState(RouteParams params) { return new State(); }
 *
 *     @Override protected UiNode render(State s) {
 *         return UiStack.of("counter")
 *             .child(UiText.of("count", "Clicked " + s.count + " times"))
 *             .child(on(UiAction.primary("inc", "+1")).click(e -> s.count++));
 *     }
 * }
 * }</pre>
 *
 * <p><b>The contract.</b> {@code render} is a pure function of the state: it
 * is called again after every event, and everything it binds with
 * {@link #on(UiNode)} is bound again. Listeners therefore never have to
 * survive between requests — only the state does, as JSON. A handler changes
 * the state (or calls {@link #toast}, {@link #navigate}); the framework
 * re-renders, diffs the new tree against the one the client has, and sends
 * the difference.
 *
 * <p><b>Ids.</b> Every node that receives events needs an {@code id}, and ids
 * should be stable across renders — derive them from your data
 * ({@code "order-" + order.id()}), never from a counter. An id that changes on
 * every render costs a {@code REPLACE} on every event.
 *
 * <p><b>State.</b> {@code S} is a plain JSON-able class the view owns —
 * public fields or getters/setters, a no-arg constructor. Collaborators
 * (services, repositories) come through the view's constructor and are never
 * part of the state; under Spring Boot the view is instantiated per request
 * with constructor injection.
 *
 * <p><b>Dialogs.</b> An open dialog is state, not an event: decide in
 * {@code render} whether to call {@link #dialog(UiDialog)}, and the diff opens
 * or closes it on the client.
 */
public abstract class SuiView<S> {

    // ── set by the engine ──────────────────────────────────────────────────
    private ViewContext context;
    // ── per render ─────────────────────────────────────────────────────────
    private Map<String, UiEventHandler> handlers;
    private List<UiDialog> dialogs;
    private String title;
    // ── per event ──────────────────────────────────────────────────────────
    private final List<UiToast> toasts = new ArrayList<>();
    private String navigateTo;

    /**
     * The state a fresh instance starts with. {@code params} are the query
     * parameters of the {@code GET} that opened the view.
     */
    protected abstract S initialState(RouteParams params);

    /** The tree for this state. Bind listeners with {@link #on(UiNode)} while building it. */
    protected abstract UiNode render(S state);

    // ── binding ────────────────────────────────────────────────────────────

    /**
     * Binds listeners to {@code node}. Each binder method sets the matching
     * trigger on the node, registers the handler for this render, and hands
     * the node back so the call slots into the existing builders:
     *
     * <pre>{@code
     * form.action(on(UiAction.primary("save", "Save")).click(e -> save(s, e)));
     * }</pre>
     *
     * <p>To listen for two events on one node, call {@code on(...)} twice.
     */
    protected final <N extends UiNode> Binder<N> on(N node) {
        return new Binder<>(this, node, false);
    }

    /**
     * Like {@link #on(UiNode)} for a {@code UiTable} row action: the trigger
     * keeps the core's {@code {id}} placeholder, so the handler learns which
     * row through {@link UiEvent#rowId()}.
     */
    protected final <N extends UiNode> Binder<N> onRow(N node) {
        return new Binder<>(this, node, true);
    }

    // ── render-time declarations ───────────────────────────────────────────

    /**
     * Declares a dialog as open for this render. Call it from {@code render}
     * whenever the state says the dialog is open; leave it out to close it.
     */
    protected final void dialog(UiDialog dialog) {
        requireRendering();
        Objects.requireNonNull(dialog.getId(), "a dialog needs an id");
        dialogs.add(dialog);
    }

    /** The browser title for this render; {@code null} keeps the default. */
    protected final void title(String title) {
        requireRendering();
        this.title = title;
    }

    // ── intents, from handlers ─────────────────────────────────────────────

    /** Shows a toast with this response. */
    protected final void toast(UiToast toast) {
        toasts.add(toast);
    }

    /** {@link #toast(UiToast)} with an info toast. */
    protected final void toast(String message) {
        toasts.add(UiToast.info(message));
    }

    /**
     * Leaves this view for {@code url} once the handler returns. The response
     * becomes a page navigation instead of a patch; the instance stays in the
     * store until it expires, so the back button still finds it.
     */
    protected final void navigate(String url) {
        this.navigateTo = url;
    }

    /** {@link #navigate(String)} to another view's route. */
    protected final void navigate(Class<? extends SuiView<?>> view) {
        navigate(routeOf(view));
    }

    /** {@link #navigate(String)} to another view's route with query parameters. */
    protected final void navigate(Class<? extends SuiView<?>> view, Map<String, String> params) {
        var sb = new StringBuilder(routeOf(view));
        char sep = '?';
        for (var e : params.entrySet()) {
            sb.append(sep).append(java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8))
              .append('=').append(java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
            sep = '&';
        }
        navigate(sb.toString());
    }

    /** Where this instance lives; available during render and in handlers. */
    protected final ViewContext context() {
        if (context == null) throw new IllegalStateException("view is not attached to an instance yet");
        return context;
    }

    /** The route declared on a view class. */
    public static String routeOf(Class<?> view) {
        SuiRoute route = view.getAnnotation(SuiRoute.class);
        if (route == null) {
            throw new IllegalArgumentException(view.getName() + " has no @SuiRoute");
        }
        return route.value();
    }

    // ── framework side ─────────────────────────────────────────────────────
    // Public so the engine can drive the view from another package; not part
    // of the API a view author uses.

    /** Called by the engine before a render. */
    public final void attach(ViewContext context) {
        this.context = context;
    }

    /** Runs {@link #render} and collects what it bound and declared. */
    public final Rendered doRender(S state) {
        handlers = new LinkedHashMap<>();
        dialogs = new ArrayList<>();
        title = null;
        try {
            UiNode node = render(state);
            return new Rendered(node, List.copyOf(dialogs), Map.copyOf(handlers), title);
        } finally {
            handlers = null;
            dialogs = null;
        }
    }

    /** Toasts and navigation a handler asked for, cleared on read. */
    public final Intents drainIntents() {
        var out = new Intents(List.copyOf(toasts), navigateTo);
        toasts.clear();
        navigateTo = null;
        return out;
    }

    /** What one render produced. */
    public record Rendered(UiNode node, List<UiDialog> dialogs,
                           Map<String, UiEventHandler> handlers, String title) {
        public UiEventHandler handler(String nodeId, String event) {
            return handlers.get(key(nodeId, event));
        }
    }

    /** What a handler asked the response to do besides carrying the diff. */
    public record Intents(List<UiToast> toasts, String navigateTo) {
    }

    static String key(String nodeId, String event) {
        return nodeId + "/" + event;
    }

    void bind(UiNode node, String event, UiEventHandler handler) {
        requireRendering();
        if (node.getId() == null || node.getId().isBlank()) {
            throw new IllegalStateException("a node needs an id to receive events: " + node);
        }
        String key = key(node.getId(), event);
        if (handlers.containsKey(key)) {
            throw new IllegalStateException("two handlers bound for " + key
                    + " — node ids must be unique within a view");
        }
        handlers.put(key, Objects.requireNonNull(handler, "handler"));
    }

    private void requireRendering() {
        if (handlers == null) {
            throw new IllegalStateException("only allowed while render() runs");
        }
    }
}
