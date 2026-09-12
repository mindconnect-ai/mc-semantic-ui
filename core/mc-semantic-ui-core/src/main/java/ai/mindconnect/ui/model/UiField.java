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
     * of the checked ones; unchecking returns it to its place among the rest,
     * which keep option order. The move buttons need the SPA EventBus (or
     * JavaFX); a page rendered without it hides them.
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
     * One option as a choice field shows it: the option, its position in
     * {@link #options} (stable across reorders — renderers derive ids from it),
     * and whether it starts checked.
     */
    public record Choice(Option option, int index, boolean checked) { }

    /**
     * The options in the order a choice field shows them, each marked checked
     * or not — the rule every renderer shares: SSR, the SPA
     * ({@code renderers/choices.ts}, kept in step by shared fixtures) and
     * JavaFX, for dropdowns and expanded groups alike.
     *
     * <ul>
     *   <li>checked follows {@link #isChosen};</li>
     *   <li>an {@link #orderable} MULTISELECT shows its checked options first,
     *       in the order of {@link #value}, then the rest in option order;</li>
     *   <li>anything else keeps option order;</li>
     *   <li>a null entry in {@link #options} is not an option and is skipped
     *       (the indexes of the others do not move); options sharing a value
     *       are both shown, and both checked when that value is.</li>
     * </ul>
     */
    public List<Choice> choicesInDisplayOrder() {
        List<Option> all = options == null ? List.of() : options;
        var selected = fieldType == FieldType.MULTISELECT ? selectedValues() : null;
        var single = fieldType == FieldType.MULTISELECT ? null : valueText(value);
        var choices = new ArrayList<Choice>(all.size());
        for (int i = 0; i < all.size(); i++) {
            var option = all.get(i);
            if (option == null) continue;
            var v = option.getValue();
            boolean checked = v != null && (selected != null ? selected.contains(v) : v.equals(single));
            choices.add(new Choice(option, i, checked));
        }
        if (!orderable || selected == null) return choices;
        var rank = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < selected.size(); i++) rank.putIfAbsent(selected.get(i), i);
        var ordered = new ArrayList<Choice>(choices.size());
        // Stream.sorted is stable, so options sharing a value keep option order.
        choices.stream().filter(Choice::checked)
                .sorted(java.util.Comparator.comparingInt(c -> rank.get(c.option().getValue())))
                .forEach(ordered::add);
        choices.stream().filter(c -> !c.checked()).forEach(ordered::add);
        return ordered;
    }

    /**
     * Whether the option with this value is chosen: for a MULTISELECT when
     * {@link #selectedValues} holds it, otherwise when it equals {@link #value}
     * as text ({@link #valueText}). An option without a value is never chosen.
     */
    public boolean isChosen(String optionValue) {
        if (optionValue == null) return false;
        if (fieldType == FieldType.MULTISELECT) return selectedValues().contains(optionValue);
        return optionValue.equals(valueText(value));
    }

    /**
     * The values of a multi-choice {@link #value}: a collection or array (null
     * entries dropped), or a comma-separated string with each part trimmed —
     * empty parts included, so {@code "a,"} is {@code ["a", ""]}. Null or a
     * blank string means none. Each value is taken as text by {@link #valueText}.
     */
    public List<String> selectedValues() {
        if (value == null) return List.of();
        if (value instanceof java.util.Collection<?> || value.getClass().isArray()) {
            return elements(value).stream().filter(java.util.Objects::nonNull).map(UiField::valueText).toList();
        }
        var text = valueText(value);
        if (JS_WHITESPACE_ONLY.matcher(text).matches()) return List.of();
        return java.util.Arrays.stream(text.split(",", -1)).map(UiField::jsTrim).toList();
    }

    /**
     * A value as the browser sees it once it has travelled as JSON — the text
     * JavaScript's {@code String(value)} gives — so SSR and JavaFX compare
     * values exactly like the SPA: a whole-number double is {@code "2"}, not
     * {@code "2.0"}; a BigDecimal drops trailing zeros; a collection or array
     * joins its elements with commas. Null stays null.
     */
    public static String valueText(Object value) {
        if (value == null) return null;
        if (value instanceof java.util.Collection<?> || value.getClass().isArray()) {
            return String.join(",", elements(value).stream().map(e -> e == null ? "" : valueText(e)).toList());
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 1e21) {
                return new java.math.BigDecimal(d).toPlainString();
            }
            return Double.isNaN(d) ? "NaN" : Double.isInfinite(d) ? (d > 0 ? "Infinity" : "-Infinity") : Double.toString(d);
        }
        if (value instanceof java.math.BigDecimal bd) {
            return bd.signum() == 0 ? "0" : bd.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    /**
     * Where a row of an orderable group belongs after its box was ticked or
     * unticked, given the rows as shown (the toggled one already carrying its
     * new state); returns the row's index in the final order. Mirrors
     * {@code seatAfterToggle} in {@code renderers/choices.ts} — both are held to
     * the fixtures in {@code src/test/resources/fixtures/choice-seats.json}.
     *
     * <p>Checked rows lead; the rest follow in option order. A ticked row that
     * is already among the leading checked rows stays put, otherwise it joins
     * the end of them; an unticked row returns to its option place among the
     * unchecked rows — where a server re-render puts it too.
     *
     * @param checked each row's checked state, in the order shown
     * @param index   each row's position in the field's options
     * @param at      the row that was toggled
     */
    public static int seatAfterToggle(List<Boolean> checked, List<Integer> index, int at) {
        int checkedOthers = 0;
        for (int i = 0; i < checked.size(); i++) if (i != at && checked.get(i)) checkedOthers++;
        if (checked.get(at)) {
            boolean inBlock = true;
            for (int i = 0; i < at; i++) inBlock &= checked.get(i);
            return inBlock ? at : checkedOthers;
        }
        int seat = checkedOthers;
        int others = 0;
        for (int i = 0; i < checked.size(); i++) {
            if (i == at) continue;
            // The first checkedOthers rows other than this one are the checked block.
            if (others++ < checkedOthers) continue;
            if (index.get(i) > index.get(at)) break;
            seat++;
        }
        return seat;
    }

    /** JavaScript's notion of whitespace, which {@code String.prototype.trim} removes. */
    private static final java.util.regex.Pattern JS_WHITESPACE_ONLY =
            java.util.regex.Pattern.compile("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]*");
    private static final java.util.regex.Pattern JS_TRIM =
            java.util.regex.Pattern.compile("^[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+|[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+$");

    private static String jsTrim(String text) {
        return JS_TRIM.matcher(text).replaceAll("");
    }

    private static List<Object> elements(Object collectionOrArray) {
        if (collectionOrArray instanceof java.util.Collection<?> c) return new ArrayList<>(c);
        int n = java.lang.reflect.Array.getLength(collectionOrArray);
        var out = new ArrayList<Object>(n);
        for (int i = 0; i < n; i++) out.add(java.lang.reflect.Array.get(collectionOrArray, i));
        return out;
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
