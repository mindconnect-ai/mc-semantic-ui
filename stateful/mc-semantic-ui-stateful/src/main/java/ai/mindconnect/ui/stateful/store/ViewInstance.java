package ai.mindconnect.ui.stateful.store;

import lombok.Data;

import java.time.Instant;

/**
 * One materialised view for one owner in one browser tab, as the store keeps
 * it. Two documents ride inside: {@link #stateJson} is the source of truth,
 * {@link #pageJson} is the last page the client received — a cache the next
 * diff runs against, and nothing the framework cannot live without.
 */
@Data
public class ViewInstance {

    /** 128-bit random, URL-safe. */
    private String id;
    private String route;
    /** Class name of the view; resolved through the registry, never loaded from here. */
    private String viewClass;
    /**
     * Who may send events: the authenticated principal, or a
     * {@code "session:<id>"} marker for anonymous users.
     */
    private String owner;
    /** Bumped on every applied event; the store's optimistic lock. */
    private long version;
    /** The state object as JSON. */
    private String stateJson;
    /** The last {@code UiPage} the client got (node + dialogs, no toasts) as JSON. */
    private String pageJson;
    /**
     * Toasts a handler produced on the no-JS path, to be shown by the page the
     * redirect lands on. Null or {@code "[]"} normally.
     */
    private String pendingToastsJson;
    private Instant createdAt;
    private Instant touchedAt;

    public ViewInstance copy() {
        var c = new ViewInstance();
        c.id = id;
        c.route = route;
        c.viewClass = viewClass;
        c.owner = owner;
        c.version = version;
        c.stateJson = stateJson;
        c.pageJson = pageJson;
        c.pendingToastsJson = pendingToastsJson;
        c.createdAt = createdAt;
        c.touchedAt = touchedAt;
        return c;
    }
}
