package ai.mindconnect.ui.javafx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Collects the current values of every control inside one {@code <form>} —
 * the JavaFX answer to the SPA's "walk every named control inside the form
 * element" payload collection.
 *
 * <p>Each field renderer registers a value supplier under the field's id while
 * painting; {@link #values()} reads them all back on demand. Because the scope
 * is threaded through {@link FxRenderContext}, it does not matter how deeply a
 * field is nested — inside a {@code UiStack}, inside a tab of a
 * {@code UiSection} — it still rides along in the one submit, exactly like the
 * web renderers.
 */
public class FxFormScope {

    private final String formId;
    private final Map<String, Supplier<Object>> sources = new LinkedHashMap<>();
    private Runnable submit = () -> { };

    public FxFormScope(String formId) {
        this.formId = formId;
    }

    public String formId() {
        return formId;
    }

    /**
     * Installs what "submitting this form" means — set by the form renderer to
     * fire the form's first action. Backs
     * {@link ai.mindconnect.ui.model.UiField#isSubmitOnChange()} and
     * {@link ai.mindconnect.ui.model.UiField#isSubmitOnEnter()}, the two field
     * flags that commit without a separate Save button.
     */
    public void onSubmit(Runnable submit) {
        this.submit = submit == null ? () -> { } : submit;
    }

    /** Fires the form's submit. No-op when the form has no action to submit to. */
    public void submit() {
        submit.run();
    }

    /**
     * Registers a control's live value. Called by the field renderers; the
     * supplier is read at submit time, not at render time, so it always
     * reflects what the user typed last.
     */
    public void register(String fieldId, Supplier<Object> valueSupplier) {
        if (fieldId == null || fieldId.isBlank()) return;
        sources.put(fieldId, valueSupplier);
    }

    /** The payload for a submit: field id → current value. */
    public Map<String, Object> values() {
        var out = new LinkedHashMap<String, Object>();
        sources.forEach((id, supplier) -> out.put(id, supplier.get()));
        return out;
    }

    /** Current value of a single field, or {@code null} if it isn't in this form. */
    public Object value(String fieldId) {
        var supplier = sources.get(fieldId);
        return supplier == null ? null : supplier.get();
    }
}
