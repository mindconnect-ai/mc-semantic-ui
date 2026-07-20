package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiForm extends UiNode {
    private List<UiField>  fields  = new ArrayList<>();
    private List<UiAction> actions = new ArrayList<>();
    private List<UiLink>   links   = new ArrayList<>();
    /**
     * Optional rich body rendered inside the {@code <form>} <em>after</em> the
     * flat {@link #fields}. Any {@link UiNode} is allowed — a {@link UiStack}
     * for columns, a {@link UiSection} for tabs, nested groups — so a form can
     * be structured freely. Because the client collects the payload by walking
     * <em>every</em> named control inside the {@code <form>} element (not this
     * list), the whole form is still submitted as one object, no matter how the
     * fields are laid out or which tab they sit in.
     *
     * <p>Use {@link #fields} for a plain vertical form; use {@link #content}
     * (or both) when you need layout. Put the actual inputs as standalone
     * {@link UiField} nodes inside the content tree.
     */
    private List<UiNode> content;
    /**
     * Form-level error banner shown above the fields — for cross-field or
     * general errors ("Please fix the errors below", "Save failed"). Per-field
     * errors go on {@link UiField#getValidationError()} instead.
     */
    private String formError;
    /**
     * When {@code true}, submitting this form bypasses the SPA EventBus
     * and triggers a native browser navigation (full page reload). Used
     * for state-changing actions whose effect lives <em>outside</em> the
     * SPA-mounted {@code #sui-root} subtree — e.g. swapping the
     * stylesheet in {@code <head>} (theme switch) or replacing the SPA
     * bootstrap with an SSR-only response (mode switch). Without this
     * flag the EventBus would intercept the submit, fetch the new page
     * as JSON, and apply only the body — leaving the old {@code <link>}
     * tag (and the running EventBus instance) in place.
     */
    private boolean reloadOnSubmit;

    public UiForm field(UiField field)    { fields.add(field);   return this; }
    public UiForm action(UiAction action) { actions.add(action); return this; }
    public UiForm link(UiLink link)       { links.add(link);     return this; }

    /**
     * Appends a layout node to the form body — a {@link UiStack} (columns), a
     * {@link UiSection} (tabs), a grouped sub-tree, … See {@link #content}.
     * Put the inputs as standalone {@link UiField}s inside it; they still ride
     * along in the single form submit.
     */
    public UiForm content(UiNode node) {
        if (this.content == null) this.content = new ArrayList<>();
        this.content.add(node);
        return this;
    }

    /** Marks this form so its submit forces a full page reload, see {@link #reloadOnSubmit}. */
    public UiForm reloadOnSubmit() {
        this.reloadOnSubmit = true;
        return this;
    }

    /** Sets the form-level error banner. See {@link #formError}. */
    public UiForm error(String message) {
        this.formError = message;
        return this;
    }

    public static UiForm of(String id, String title) {
        var f = new UiForm();
        f.setId(id); f.setTitle(title);
        return f;
    }
}
