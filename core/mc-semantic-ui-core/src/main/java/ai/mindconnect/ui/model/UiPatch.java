package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiPatch {

    public enum Op { REPLACE, APPEND, CLEAR, REMOVE }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Operation {
        private Op     op;
        private String targetId;
        private UiNode node;

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
