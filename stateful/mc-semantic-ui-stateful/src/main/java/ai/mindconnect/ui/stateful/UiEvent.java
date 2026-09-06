package ai.mindconnect.ui.stateful;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One user interaction, as the server sees it: which node, which event, what
 * the enclosing form held, and — for row actions — which row.
 *
 * <p>The payload is what the core client harvests from the trigger's payload
 * node (by default the enclosing {@code UiForm}): input values keyed by field
 * id. A field left empty arrives as an empty string, a checkbox as
 * {@code true}/{@code false}, a multi-select as a list. Accessors below do
 * the common conversions; {@link #payload()} has the raw map.
 */
public final class UiEvent {

    /** A file that came with an upload event. */
    public interface Attachment {
        /** The multipart field name. */
        String name();
        /** The original file name as the browser sent it, may be null. */
        String filename();
        String contentType();
        long size();
        InputStream open() throws IOException;
    }

    private final String nodeId;
    private final String event;
    private final String rowId;
    private final Map<String, Object> payload;
    private final List<Attachment> attachments;
    private final String principal;

    public UiEvent(String nodeId, String event, String rowId,
                   Map<String, Object> payload, List<Attachment> attachments, String principal) {
        this.nodeId = nodeId;
        this.event = event;
        this.rowId = rowId;
        this.payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        this.attachments = attachments == null ? List.of() : List.copyOf(attachments);
        this.principal = principal;
    }

    /** Id of the node the listener was bound to. */
    public String nodeId() {
        return nodeId;
    }

    /** {@code "click"}, {@code "change"}, {@code "input"}, {@code "dblclick"}, {@code "hover"}, {@code "leave"}, {@code "upload"}. */
    public String event() {
        return event;
    }

    /**
     * For a table row action: the row's id, taken from the core's {@code {id}}
     * placeholder that both renderers substitute per row. {@code null} for
     * anything that is not a row action.
     */
    public String rowId() {
        return rowId;
    }

    /** The harvested form values, raw. */
    public Map<String, Object> payload() {
        return payload;
    }

    /** A payload value as text; {@code null} when absent, the first entry when it is a list. */
    public String value(String name) {
        Object v = payload.get(name);
        if (v instanceof List<?> list) v = list.isEmpty() ? null : list.get(0);
        return v == null ? null : String.valueOf(v);
    }

    /** {@link #value(String)}, or {@code fallback} when absent or blank. */
    public String value(String name, String fallback) {
        String v = value(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** A payload value as an int, {@code fallback} when absent or not a number. */
    public int intValue(String name, int fallback) {
        String v = value(name);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A payload value as a boolean: {@code true}, {@code "true"}, {@code "on"} and {@code "1"} count. */
    public boolean boolValue(String name) {
        Object v = payload.get(name);
        if (v instanceof Boolean b) return b;
        String s = value(name);
        return s != null && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on") || s.equals("1"));
    }

    /** All values of a multi-valued field; a single value becomes a one-element list. */
    @SuppressWarnings("unchecked")
    public List<String> values(String name) {
        Object v = payload.get(name);
        if (v == null) return List.of();
        if (v instanceof List<?> list) return ((List<Object>) list).stream().map(String::valueOf).toList();
        return List.of(String.valueOf(v));
    }

    /** Files of an {@code upload} event; empty otherwise. */
    public List<Attachment> attachments() {
        return attachments;
    }

    /** Name of the authenticated user, or {@code null} when anonymous. */
    public String principal() {
        return principal;
    }

    @Override
    public String toString() {
        return "UiEvent[" + nodeId + "/" + event + (rowId != null ? " row=" + rowId : "") + "]";
    }
}
