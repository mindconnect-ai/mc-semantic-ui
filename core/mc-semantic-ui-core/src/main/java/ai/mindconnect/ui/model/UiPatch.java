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
         * Changes only the named fields of the target, leaving the rest alone.
         *
         * <p>{@link Op#REPLACE} needs the whole node, so flipping one flag
         * means the server rebuilding and resending a subtree it did not
         * otherwise touch — and any client state inside it is at the mercy of
         * how well the re-render reconciles. A merge says the one thing that
         * changed:
         *
         * <pre>{@code
         * UiPatch.of().patch(UiPatch.Operation.merge("save",
         *         Map.of("enabled", false, "label", "Saving…")));
         * }</pre>
         *
         * <p>For the commonest merge of all there are {@link #hide} and
         * {@link #show}.
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

        /** Hides the target and takes it out of the layout. */
        public static Operation hide(String targetId) {
            return hide(targetId, UiNode.Display.HIDDEN);
        }

        /**
         * Hides the target, either way of hiding:
         * {@link UiNode.Display#HIDDEN} takes it out of the layout,
         * {@link UiNode.Display#BLANK} leaves its space behind so nothing
         * around it jumps.
         */
        public static Operation hide(String targetId, UiNode.Display how) {
            return visibility(targetId, how);
        }

        /** Makes the target visible again. */
        public static Operation show(String targetId) {
            return visibility(targetId, null);
        }

        /**
         * Both of the above are merges of one field — this is that merge.
         *
         * <p>It exists rather than leaving callers to write the map because
         * the one that matters cannot be written the obvious way:
         * {@code Map.of("display", null)} throws, since Map.of forbids null
         * values. Showing something again would be the awkward case, and it is
         * the half people forget.
         */
        private static Operation visibility(String targetId, UiNode.Display how) {
            var attributes = new LinkedHashMap<String, Object>();
            attributes.put("display", how);
            return merge(targetId, attributes);
        }

        /**
         * Removes the target element itself from the DOM, including any
         * surrounding {@code <li>} wrapper when the target was rendered as
         * a list item. Used to revoke transient indicators (e.g. a
         * "thinking …" placeholder) without leaving an empty container.
         */
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
