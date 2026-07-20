package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A single column in a {@link UiTable}. Carries the column's visible name
 * ({@code label}), its data-lookup key into the row map ({@code dataKey}),
 * and its presentation hints (sortable, cell template).
 *
 * <p><b>Why a {@link UiNode}?</b> Columns are first-class structural items
 * the editor wants to add, remove, rename, and reorder. Modelling them as
 * UiNodes means they get a stable {@code id}, fit the tree picker, and
 * participate in the JSON property panel without any special-case wrappers.
 * The {@code dataKey} is kept separate from {@code id} so the column's DOM
 * id can carry the usual {@code col-…} prefix without affecting the
 * row-data lookup contract.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiColumn extends UiNode {

    /** Display label on the table header. Falls back to {@code title} via UiNode. */
    private String label;
    /**
     * Key used to look up the cell value in a row map. When null the
     * renderer falls back to {@link UiNode#getId()} — keeps simple cases
     * concise while still letting the editor split id from data-key.
     */
    private String dataKey;
    /**
     * Marks this column as sortable: the header renders as a clickable
     * control with a direction indicator.
     *
     * <p>How the sort is performed depends on the table:
     * <ul>
     *   <li>{@link UiTable#getSortTrigger()} set → clicking dispatches that
     *       trigger with {@code {column}} and {@code {direction}}
     *       substituted, so the <em>server</em> re-sorts and returns the
     *       page. This is the correct choice for paginated data, where the
     *       browser only ever holds one page.</li>
     *   <li>no sort trigger → the browser sorts the rows it currently has,
     *       best-effort (numeric when both values parse as numbers, else a
     *       locale-aware string compare; blanks last).</li>
     * </ul>
     */
    private boolean sortable;

    /**
     * Optional. When set, replaces the default plain-text cell rendering for
     * this column. The template is cloned for every row and any string field
     * (recursively, on the template node and all its descendants) is run
     * through {@code {dataKey}}-style substitution against the row's data
     * map — {@code {id}} resolves to {@link UiRow#getId()}, every other key
     * to {@code row.data.get(key)}. Unknown keys are left as-is so the
     * author sees the broken placeholder in the UI.
     *
     * <p>DOM ids inside the cloned template get a per-row suffix
     * ({@code <template-id>__<row-id>}) so HTML id uniqueness holds across
     * the table.
     *
     * <p>Any {@link UiNode} subtype is allowed. {@link UiText} is the
     * minimal "substituted string" case; {@link UiLink} turns the cell into
     * a navigation link; a {@link UiStack} of {@link UiAction}s gives you
     * per-row inline buttons.
     */
    private UiNode cellTemplate;

    /**
     * Primary factory: a column tied to a single data key with a header label.
     * Assigns a fresh, tree-unique {@code id} so multiple columns with the
     * same {@code dataKey} (rare, but possible in the editor) don't collide
     * in the DOM. The semantic cell-rendering (link, action, editable field,
     * …) is opt-in via {@link #setCellTemplate(UiNode)}.
     */
    public static UiColumn of(String dataKey, String label) {
        var c = new UiColumn();
        c.setId("col-" + dataKey + "-" + shortNonce());
        c.dataKey = dataKey;
        c.label = label;
        return c;
    }

    // ── Legacy factory aliases ────────────────────────────────────────────
    // The text/date/number variants used to set a FieldType cell-type hint
    // that no renderer ever read. They now alias to {@link #of} so existing
    // demo code keeps compiling; new code should use of() and attach a
    // cellTemplate if the cell needs more than plain text.

    public static UiColumn text(String dataKey, String label)   { return of(dataKey, label); }
    public static UiColumn date(String dataKey, String label)   { return of(dataKey, label); }
    public static UiColumn number(String dataKey, String label) { return of(dataKey, label); }

    /** 4-char base36 nonce; collisions within a single UiTable are vanishingly unlikely. */
    private static String shortNonce() {
        return Long.toString(Math.abs(System.nanoTime()), 36).substring(0, 4);
    }

    public UiColumn asSortable()   { this.sortable = true;   return this; }

    public UiColumn withCellTemplate(UiNode of) {
        this.cellTemplate = of;
        return this;
    }
}
