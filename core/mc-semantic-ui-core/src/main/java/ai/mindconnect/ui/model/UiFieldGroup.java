package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A titled group of related fields, rendered as a native
 * {@code <fieldset><legend>…</legend></fieldset>} — so the group heading is
 * announced for every field inside (accessibility), with less ceremony than a
 * styled {@link UiStack}.
 *
 * <p>The body holds any {@link UiNode} (usually {@link UiField}s, but a nested
 * {@link UiStack} for columns works too). A group is transparent to form
 * submission: the fields inside still carry their {@code name}, and the client
 * collects every named control in the surrounding {@code <form>}, so the whole
 * form submits as one object regardless of grouping.
 *
 * <p>Drop it into {@link UiForm#getContent()} (or any layout tree).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiFieldGroup extends UiNode {

    /** Optional helper text shown under the legend. */
    private String hint;
    /** The grouped nodes — fields, or any layout node. */
    private List<UiNode> content = new ArrayList<>();

    /** Adds a field to the group. */
    public UiFieldGroup field(UiField field) { content.add(field); return this; }

    /** Adds any node to the group (e.g. a column {@link UiStack}). */
    public UiFieldGroup content(UiNode node) { content.add(node); return this; }

    public UiFieldGroup hint(String hint) { this.hint = hint; return this; }

    /** @param title the group legend. */
    public static UiFieldGroup of(String id, String title) {
        var g = new UiFieldGroup();
        g.setId(id);
        g.setTitle(title);
        return g;
    }
}
