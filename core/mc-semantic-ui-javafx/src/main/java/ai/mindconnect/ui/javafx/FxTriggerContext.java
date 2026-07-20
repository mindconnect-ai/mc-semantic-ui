package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTrigger;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * What a behaviour or client handler is handed when a trigger fires: the
 * trigger itself, the node that fired it, and the resolved payload.
 *
 * @param bus     the owning bus, so a handler can dispatch follow-on triggers
 * @param trigger the trigger that fired
 * @param source  the model node the trigger sat on (a {@code UiAction}, a
 *                {@code UiField}, a clickable row, …)
 * @param render  the render context the source was painted in — gives access
 *                to the enclosing form and to the node index
 * @param payload the collected values of the node named by
 *                {@link UiTrigger#getPayload()}, or an empty map when the
 *                trigger carries no payload reference
 * @param files   files picked by a {@code FILE} field or an upload zone;
 *                empty unless the source was one
 */
public record FxTriggerContext(
        SuiFxEventBus bus,
        UiTrigger trigger,
        UiNode source,
        FxRenderContext render,
        Map<String, Object> payload,
        List<File> files
) {

    /** Convenience: a single payload value, or {@code null}. */
    public Object value(String fieldId) {
        return payload.get(fieldId);
    }

    /** Convenience: a payload value as a string, or {@code null}. */
    public String string(String fieldId) {
        var v = payload.get(fieldId);
        return v == null ? null : v.toString();
    }
}
