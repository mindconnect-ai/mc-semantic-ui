package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiField extends UiNode {

    public enum FieldType {
        TEXT, TEXTAREA, NUMBER, CURRENCY, PERCENT,
        DATE, DATETIME,
        BOOLEAN,
        SELECT, MULTISELECT,
        FILE, REFERENCE,
        /** Masked input with a built-in eye toggle that reveals the value. */
        PASSWORD,
        /**
         * Not shown at all — no label, no control, just the value riding along
         * in the form submission ({@code <input type="hidden">}). Submitted
         * whether or not the field is {@link #editable}. For a visible field
         * that is only temporarily out of view, use {@link UiNode#hidden()}.
         */
        HIDDEN
    }

    @Data
    public static class Option {
        private String value;
        private String label;

        public static Option of(String value, String label) {
            var o = new Option();
            o.value = value; o.label = label;
            return o;
        }
    }

    private String label;
    /**
     * The semantic kind of input (TEXT / SELECT / DATE / …). Renamed from
     * {@code type} so it no longer collides with the polymorphic UiNode
     * discriminator (which Jackson writes as {@code "type":"field"}).
     */
    private FieldType fieldType;
    private Object value;
    private boolean editable;
    private boolean required;
    private String placeholder;
    private String hint;
    /**
     * Leading in-field icon token (e.g. {@code "search"} on a filter box).
     * Decorative; only meaningful for editable single-line controls. See
     * {@link UiIcon}.
     */
    private String icon;
    private String validationError;
    private List<Option> options;
    /**
     * Lower bound for {@link FieldType#DATE}, {@link FieldType#DATETIME},
     * {@link FieldType#NUMBER}, {@link FieldType#CURRENCY},
     * {@link FieldType#PERCENT}. Rendered verbatim as the {@code min}
     * attribute on the input. Format expected by the browser:
     * {@code yyyy-MM-dd} (date), {@code yyyy-MM-ddTHH:mm} (datetime), or a
     * plain number string.
     */
    private String min;
    /** Upper bound. See {@link #min}. */
    private String max;
    /**
     * Step granularity for numeric / date inputs. Examples:
     * {@code "0.01"} for currency, {@code "1"} for integer NUMBER,
     * {@code "60"} (seconds) for finer DATETIME control. Rendered as the
     * {@code step} attribute.
     */
    private String step;
    /**
     * Only meaningful for {@link FieldType#TEXTAREA}: when {@code true},
     * pressing Enter inside the textarea submits the surrounding
     * {@link UiForm} (Shift+Enter still inserts a newline). Used for
     * chat-style inputs where Enter is the natural commit gesture.
     */
    private boolean submitOnEnter;

    /**
     * When {@code true}, changing the field's value (typing in a text input,
     * picking from a {@code <select>}, toggling a checkbox) immediately
     * submits the surrounding {@link UiForm}. Used for "instant" controls
     * like a theme switcher dropdown where the user's selection IS the
     * action — no separate Save button needed. SSR side: native browser
     * behaviour ({@code onchange="this.form.submit()"}); SPA side: the
     * EventBus listens for {@code change} on {@code data-submit-on-change}
     * elements.
     */
    private boolean submitOnChange;


    /**
     * Optional action rendered on the same row, right of the control — e.g. a
     * "Browse…" button next to a path field. Editable fields only.
     */
    private UiAction trailing;

    /**
     * Only for {@link FieldType#FILE}: the HTML {@code accept} attribute
     * restricting the file picker (e.g. {@code "image/*"} or
     * {@code ".pdf,.docx"}). Null = any file.
     */
    private String accept;
    /** Only for {@link FieldType#FILE}: allow selecting more than one file. */
    private boolean multiple;

    /**
     * Only for {@link FieldType#SELECT} and {@link FieldType#MULTISELECT}: show
     * every option at once instead of a dropdown or list box — a radio button
     * per option for SELECT, a checkbox per option for MULTISELECT. Purely
     * presentation: the submitted value keeps its shape (one string, or a list
     * of strings), so the server reads it exactly as before.
     */
    private boolean expanded;

    /**
     * Only for an {@link #expanded} {@link FieldType#MULTISELECT}: the checked
     * options come first and carry move-up/move-down buttons, and the value is
     * submitted in the order shown. Checking an option appends it to the end
     * of the checked ones; unchecking drops it back among the rest.
     */
    private boolean orderable;

    // ── factory methods ───────────────────────────────────────────────────

    public static UiField text(String id, String label, Object value) {
        return of(id, label, FieldType.TEXT, value);
    }

    /** Masked input with an eye toggle that reveals the value while pressed. */
    public static UiField password(String id, String label, Object value) {
        return of(id, label, FieldType.PASSWORD, value);
    }

    /**
     * A value the form submits without showing it — an id, a version, the
     * context the server needs back. Renders as {@code <input type="hidden">};
     * see {@link FieldType#HIDDEN}.
     */
    public static UiField hidden(String id, Object value) {
        return of(id, null, FieldType.HIDDEN, value);
    }

    public static UiField textarea(String id, String label, Object value) {
        return of(id, label, FieldType.TEXTAREA, value);
    }

    public static UiField number(String id, String label, Object value) {
        return of(id, label, FieldType.NUMBER, value);
    }

    public static UiField date(String id, String label, Object value) {
        return of(id, label, FieldType.DATE, value);
    }

    public static UiField bool(String id, String label, boolean value) {
        return of(id, label, FieldType.BOOLEAN, value);
    }

    public static UiField select(String id, String label, Object value, List<Option> options) {
        var f = of(id, label, FieldType.SELECT, value);
        f.options = options;
        return f;
    }

    public static UiField multiselect(String id, String label, Object value, List<Option> options) {
        var f = of(id, label, FieldType.MULTISELECT, value);
        f.options = options;
        return f;
    }

    public static UiField reference(String id, String label, Object value) {
        return of(id, label, FieldType.REFERENCE, value);
    }

    /**
     * A file-picker field ({@code <input type="file">}). Pair it with
     * {@code .onChange(UiTrigger.upload(url))} to upload on selection, or read
     * the files client-side via an {@code INVOKE} handler. For a drag-and-drop
     * zone use {@link UiUpload} instead.
     */
    public static UiField file(String id, String label) {
        return of(id, label, FieldType.FILE, null).asEditable();
    }

    private static UiField of(String id, String label, FieldType fieldType, Object value) {
        var f = new UiField();
        f.setId(id); f.label = label; f.fieldType = fieldType; f.value = value;
        return f;
    }

    // ── fluent setters ────────────────────────────────────────────────────

    public UiField asEditable() {
        this.editable = true;
        return this;
    }

    public UiField asRequired() {
        this.required = true;
        return this;
    }

    public UiField placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public UiField hint(String hint) {
        this.hint = hint;
        return this;
    }

    /** Set the leading in-field icon token (fluent). */
    public UiField icon(String iconToken) {
        this.icon = iconToken;
        return this;
    }

    public UiField error(String error) {
        this.validationError = error;
        return this;
    }

    public UiField editableIf(boolean condition) {
        return condition ? asEditable() : this;
    }

    /**
     * Enables Enter-to-submit behaviour for a {@link FieldType#TEXTAREA}
     * field — pressing Enter submits the surrounding form, Shift+Enter
     * inserts a newline. No effect on other field types (the renderer
     * only emits the {@code data-submit-on-enter} marker for textareas).
     */
    public UiField submitOnEnter() {
        this.submitOnEnter = true;
        return this;
    }

    /** Enables auto-submit on value change. See {@link #submitOnChange}. */
    public UiField submitOnChange() {
        this.submitOnChange = true;
        return this;
    }

    /** Renders {@code action} on the control's row, right of the input. See {@link #trailing}. */
    public UiField trailing(UiAction action) {
        this.trailing = action;
        return this;
    }

    /** Fires {@code trigger} when the field's value changes. See {@link #onChange}. */
    public UiField onChange(UiTrigger trigger) {
        setOnChange(trigger);
        return this;
    }

    /** Restricts the file picker (FILE fields). See {@link #accept}. */
    public UiField accept(String accept) {
        this.accept = accept;
        return this;
    }

    /** Allows selecting multiple files (FILE fields). See {@link #multiple}. */
    public UiField multiple() {
        this.multiple = true;
        return this;
    }

    /** SELECT as a group of radio buttons. See {@link #expanded}. */
    public UiField asRadio() {
        this.expanded = true;
        return this;
    }

    /** MULTISELECT as a group of checkboxes. See {@link #expanded}. */
    public UiField asCheckboxes() {
        this.expanded = true;
        return this;
    }

    /**
     * MULTISELECT as checkboxes whose checked entries can be reordered; implies
     * {@link #asCheckboxes()}. See {@link #orderable}.
     */
    public UiField orderable() {
        this.expanded = true;
        this.orderable = true;
        return this;
    }

    /**
     * The options in the order an expanded field shows them. For an
     * {@link #orderable} MULTISELECT that is the checked ones in the order of
     * {@link #value}, then the rest in option order; otherwise simply
     * {@link #options}. Shared by the renderers so the first paint agrees
     * everywhere.
     */
    public List<Option> optionsInDisplayOrder() {
        List<Option> all = options == null ? List.of() : options;
        if (!orderable) return all;
        var selected = selectedValues();
        var ordered = new ArrayList<Option>(all.size());
        for (var v : selected) {
            all.stream().filter(o -> v.equals(o.getValue()) && !ordered.contains(o))
                    .findFirst().ifPresent(ordered::add);
        }
        for (var o : all) {
            if (!ordered.contains(o)) ordered.add(o);
        }
        return ordered;
    }

    /** The values of a multi-choice {@link #value}: a list, or a comma-separated string. */
    public List<String> selectedValues() {
        if (value == null) return List.of();
        if (value instanceof java.util.Collection<?> c) {
            return c.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList();
        }
        var text = value.toString();
        if (text.isBlank()) return List.of();
        return java.util.Arrays.stream(text.split(",")).map(String::trim).toList();
    }

    /** Sets lower bound (date/number). See {@link #min}. */
    public UiField min(String min)   { this.min = min;   return this; }
    /** Sets upper bound (date/number). See {@link #max}. */
    public UiField max(String max)   { this.max = max;   return this; }
    /** Sets step granularity. See {@link #step}. */
    public UiField step(String step) { this.step = step; return this; }

    /** Convenience: short-form min/max range for a date field. */
    public UiField range(String min, String max) {
        this.min = min; this.max = max; return this;
    }
}
