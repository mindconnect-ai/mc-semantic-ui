package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiTrigger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * What a view instance knows about where it lives: its id, its route and how
 * to spell a trigger that reaches the generic event endpoint. Created by the
 * engine, attached to the view before every render.
 */
public final class ViewContext {

    private final String instanceId;
    private final String route;
    private final String eventBasePath;
    private final String instanceParam;
    private final String principal;

    public ViewContext(String instanceId, String route, String eventBasePath,
                       String instanceParam, String principal) {
        this.instanceId = instanceId;
        this.route = route;
        this.eventBasePath = eventBasePath;
        this.instanceParam = instanceParam;
        this.principal = principal;
    }

    public String instanceId() {
        return instanceId;
    }

    public String route() {
        return route;
    }

    /** Name of the authenticated user, or {@code null}. */
    public String principal() {
        return principal;
    }

    /** The address of this instance: the route plus the instance parameter. */
    public String instanceUrl() {
        return route + "?" + instanceParam + "=" + instanceId;
    }

    /**
     * The URL an event on {@code nodeId} posts to. Row actions keep the core's
     * {@code {id}} placeholder in the query, which both renderers substitute
     * per row.
     */
    public String eventUrl(String nodeId, String event, boolean rowScoped) {
        String url = eventBasePath + "/" + instanceId + "/" + encode(nodeId) + "/" + encode(event);
        return rowScoped ? url + "?row={id}" : url;
    }

    /** A plain {@code APPLY_RESPONSE} POST to {@link #eventUrl}: the core client needs nothing new. */
    public UiTrigger eventTrigger(String nodeId, String event, boolean rowScoped, String payloadNodeId) {
        return UiTrigger.api("POST", eventUrl(nodeId, event, rowScoped), payloadNodeId);
    }

    /** Same address, but as the core's {@code UPLOAD} behaviour. */
    public UiTrigger uploadTrigger(String nodeId, String event) {
        return UiTrigger.upload("POST", eventUrl(nodeId, event, false));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
