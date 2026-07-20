package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class UiTable extends UiNode {

    /**
     * @deprecated use {@link UiColumn} directly. Kept as a thin alias so
     *             existing call sites (e.g. demo controllers) keep compiling
     *             while we migrate. Will be removed once the demo + tests
     *             reference {@code UiColumn} everywhere.
     */
    @Deprecated
    public static final class Column extends UiColumn {
        public static UiColumn text(String dataKey, String label)   { return UiColumn.text(dataKey, label); }
        public static UiColumn date(String dataKey, String label)   { return UiColumn.date(dataKey, label); }
        public static UiColumn number(String dataKey, String label) { return UiColumn.number(dataKey, label); }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Pagination {
        private int page;
        private int size;
        private long total;
        /**
         * Trigger template fired when the user clicks a page button. The
         * renderer substitutes the literal {@code {page}} in the trigger's
         * {@code url} with the target page number before emitting the
         * button's {@code data-trigger} attribute. Null falls back to
         * informational-only rendering (no clickable buttons).
         */
        private UiTrigger pageTrigger;

        public static Pagination of(int page, int size, long total) {
            var p = new Pagination();
            p.page = page; p.size = size; p.total = total;
            return p;
        }

        public Pagination pageTrigger(UiTrigger t) { this.pageTrigger = t; return this; }
    }

    /**
     * Row-selection behaviour. {@link #NONE} (default) renders no selection
     * controls — the table is read-only. {@link #SINGLE} prepends a radio
     * column; {@link #MULTI} a checkbox column. The selection inputs share
     * a single form-name ({@code name="<table.id>__selection"}) so the
     * surrounding form submits the chosen row id(s) under that key.
     */
    public enum SelectMode { NONE, SINGLE, MULTI }

    private List<UiColumn>            columns       = new ArrayList<>();
    private List<UiRow>               rows          = new ArrayList<>();
    private Pagination                pagination;
    private List<UiAction>            actions       = new ArrayList<>();
    private List<UiAction>            rowActions    = new ArrayList<>();
    private String                    selectedRowId;
    private SelectMode                selectMode    = SelectMode.NONE;
    /** Pre-selected row ids — pre-checks the radio/checkbox at render time. */
    private List<String>              selectedRowIds = new ArrayList<>();
    /**
     * When true, the table collapses to a stacked card layout on a narrow
     * screen: the header row is hidden and every row becomes its own block of
     * {@code Column: value} lines (each cell carries its column label as a
     * {@code data-label}). The wide-screen layout is the normal table.
     */
    private boolean stackOnMobile;

    /** Sort direction of the currently sorted column. */
    public enum SortDirection { ASC, DESC }

    /**
     * Trigger template fired when a {@link UiColumn#isSortable() sortable}
     * header is clicked. Mirrors {@link Pagination#getPageTrigger()}: the URL
     * carries {@code {column}} and {@code {direction}} placeholders which are
     * substituted per header at render time, e.g.
     * {@code UiTrigger.go("/products?sort={column}&dir={direction}")}.
     *
     * <p>Leave it null for client-side sorting: the browser reorders the rows
     * it already has. That is right for a fully loaded table and wrong for a
     * paginated one — sorting one page of many is misleading, so set a trigger
     * whenever {@link #getPagination()} is set.
     */
    private UiTrigger sortTrigger;

    /**
     * The {@code dataKey} (or column id) the table is currently sorted by.
     * Drives the header indicator and {@code aria-sort}; the next click on
     * that same column flips {@link #sortDirection}.
     */
    private String sortColumn;

    /** Direction of {@link #sortColumn}. Defaults to ASC when a column is set. */
    private SortDirection sortDirection;

    /**
     * Optional CSS length that caps the height of the scrollable row area —
     * {@code "420px"}, {@code "60vh"}, {@code "100%"}. When set, the rows
     * scroll inside the table while the header row stays pinned to the top.
     * When null the table grows with its content and the page scrolls.
     */
    private String maxHeight;

    public UiTable column(UiColumn column)         { columns.add(column);    return this; }
    /**
     * Convenience: wraps the row map in a {@link UiRow}. Lets callers stay
     * concise ({@code .row(Map.of("sku", "X-1"))}) while the model normalises
     * to the UiNode shape for editor / Jackson polymorphism.
     */
    public UiTable row(Map<String, Object> row)    { rows.add(UiRow.of(row)); return this; }
    public UiTable row(UiRow row)                  { rows.add(row);          return this; }
    public UiTable action(UiAction action)         { actions.add(action);    return this; }
    public UiTable rowAction(UiAction action)      { rowActions.add(action); return this; }
    public UiTable selectedRow(String rowId)       { this.selectedRowId = rowId; return this; }
    public UiTable selectMode(SelectMode mode)     { this.selectMode = mode; return this; }
    public UiTable stackOnMobile(boolean stack)    { this.stackOnMobile = stack; return this; }
    public UiTable selectedRowIds(List<String> ids){ this.selectedRowIds = new ArrayList<>(ids); return this; }
    /**
     * Server-side sorting: the trigger URL should carry {@code {column}} and
     * {@code {direction}} placeholders. Without this, sortable columns sort
     * client-side.
     */
    public UiTable sortTrigger(UiTrigger trigger)  { this.sortTrigger = trigger; return this; }

    /** Marks the column currently sorted, ascending. */
    public UiTable sortedBy(String column)         { return sortedBy(column, SortDirection.ASC); }

    public UiTable sortedBy(String column, SortDirection direction) {
        this.sortColumn = column;
        this.sortDirection = direction;
        return this;
    }

    /** Caps the scrollable row area; the header row stays pinned. */
    public UiTable maxHeight(String cssLength)     { this.maxHeight = cssLength; return this; }

    public UiTable paginate(int page, int size, long total) {
        this.pagination = Pagination.of(page, size, total);
        return this;
    }
    public UiTable paginate(int page, int size, long total, UiTrigger pageTrigger) {
        this.pagination = Pagination.of(page, size, total).pageTrigger(pageTrigger);
        return this;
    }

    public static UiTable of(String id, String title) {
        var t = new UiTable();
        t.setId(id); t.setTitle(title);
        return t;
    }
}
