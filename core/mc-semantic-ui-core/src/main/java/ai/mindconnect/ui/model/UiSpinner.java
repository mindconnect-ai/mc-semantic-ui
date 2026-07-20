package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A busy indicator — a spinning glyph, optionally with a label. Use it as a
 * placeholder while content loads (an empty list body, a card that is still
 * fetching), or inline next to a status line.
 *
 * <p>This is the <em>declarative</em> spinner: a real {@link UiNode} you put in
 * the tree and later replace via a patch once the data arrives. It is distinct
 * from the <em>transient</em> loading feedback the client shows on the control
 * you just clicked — that one is wired automatically by the event bus (an
 * {@code is-loading} class on the source button/link) and needs no node.
 *
 * <p>Colour follows {@code currentColor} (default: the primary accent via CSS)
 * and size follows {@link Size}. The inherited {@code title} is used as the
 * accessible label ({@code role="status"}); {@link #label} is the visible text.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiSpinner extends UiNode {

    public enum Size { SM, MD, LG }

    /** Glyph size. Defaults to {@link Size#MD} when null. */
    private Size size;

    /** Optional visible text shown next to the glyph (e.g. {@code "Loading…"}). */
    private String label;

    public static UiSpinner of() {
        return new UiSpinner();
    }

    public static UiSpinner of(String label) {
        var s = new UiSpinner();
        s.label = label;
        return s;
    }

    public UiSpinner size(Size size) {
        this.size = size;
        return this;
    }

    public UiSpinner label(String label) {
        this.label = label;
        return this;
    }
}
