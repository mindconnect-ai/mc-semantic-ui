package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiPatch {

    public enum Op { REPLACE, APPEND, CLEAR, REMOVE, MERGE }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Operation {
        private Op     op;
        private String targetId;
        private UiNode node;

        /**
         * The fields to change, for {@link Op#MERGE}. Everything the target
         * carries and this map does not name is left exactly as it was.
         *
         * <p>Keys are the node's own JSON field names — {@code display},
         * {@code label}, {@code enabled} — and a {@code null} value clears the
         * field rather than being ignored, so a state can be turned off as
         * well as on.
         */
        private Map<String, Object> attributes;

        public static Operation replace(String targetId, UiNode node) {
            var o = new Operation();
            o.op = Op.REPLACE; o.targetId = targetId; o.node = node;
            return o;
        }

        public static Operation append(String targetId, UiNode node) {
            var o = new Operation();
            o.op = Op.APPEND; o.targetId = targetId; o.node = node;
            return o;
        }

        /** Empties the target's children, leaving the target element in place. */
        public static Operation clear(String targetId) {
            var o = new Operation();
            o.op = Op.CLEAR; o.targetId = targetId;
            return o;
        }

        /**
         * Removes the target element itself from the DOM, including any
         * surrounding {@code <li>} wrapper when the target was rendered as
         * a list item. Used to revoke transient indicators (e.g. a
         * "thinking …" placeholder) without leaving an empty container.
         */
        /**
         * Changes only the named fields of the target, leaving the rest alone.
         *
         * <p>{@link Op#REPLACE} needs the whole node, so flipping one flag
         * means the server rebuilding and resending a subtree it did not
         * otherwise touch — and any client state inside it is at the mercy of
         * how well the re-render reconciles. A merge says the one thing that
         * changed:
         *
         * <pre>{@code
         * UiPatch.of().patch(UiPatch.Operation.merge("filters",
         *         Map.of("display", UiNode.Display.HIDDEN)));
         * }</pre>
         *
         * <p>The client applies this against the node it already has, so it
         * needs to have rendered that node: see the renderers' notes on where
         * a merge finds its target.
         */
        public static Operation merge(String targetId, Map<String, Object> attributes) {
            var o = new Operation();
            o.op = Op.MERGE; o.targetId = targetId; o.attributes = attributes;
            return o;
        }

        /**
         * The common merge: show or hide the target without touching anything
         * else about it. {@code null} makes it visible again.
         */
        public static Operation display(String targetId, UiNode.Display display) {
            var attributes = new LinkedHashMap<String, Object>();
            attributes.put("display", display);   // a null value is the point, so not Map.of
            return merge(targetId, attributes);
        }

        public static Operation remove(String targetId) {
            var o = new Operation();
            o.op = Op.REMOVE; o.targetId = targetId;
            return o;
        }
    }

    private List<Operation> patches = new ArrayList<>();
    /**
     * Toasts to display alongside the patch. Same envelope as
     * {@link UiPage#getToasts()} — the EventBus consumes them after applying
     * the patch operations, so a single response can both mutate the DOM
     * <em>and</em> say "Saved." in one go.
     */
    private List<UiToast> toasts;

    public UiPatch patch(Operation op) {
        patches.add(op);
        return this;
    }

    public UiPatch toast(UiToast toast) {
        if (this.toasts == null) this.toasts = new ArrayList<>();
        this.toasts.add(toast);
        return this;
    }

    public static UiPatch of() {
        return new UiPatch();
    }
}
