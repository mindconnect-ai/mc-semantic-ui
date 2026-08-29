package ai.mindconnect.ui.javafx.renderers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxText;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiRow;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.model.UiTable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiTable} as a {@link TableView}, with the table-level actions
 * above it and the pagination footer below.
 *
 * <p>Sorting follows the model: with a {@link UiTable#getSortTrigger()} set the
 * sort is the server's (or handler's) business — clicking a header dispatches
 * the trigger instead of reordering locally. Without one, the TableView sorts
 * itself.
 *
 * <p>{@link UiColumn#getCellTemplate()} paints a column's cells as nodes
 * rather than text, with {@code {dataKey}} placeholders filled per row. Cells
 * render the raw value as text. Rich cells (a link, an inline action, an
 * editable field) come with the templating pass.
 */
public class TableRenderer implements FxNodeRenderer<UiTable> {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");
    /** Only ever used to clone and substitute cell templates. */
    private static final ObjectMapper CELL_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public Node render(UiTable node, FxRenderContext ctx) {
        var box = new VBox(8);

        if (SuiFxText.present(node.getTitle())) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-table-title");
            Icons.lead(title, node.getIcon(), ctx);
            box.getChildren().add(title);
        }

        // Whatever the server wants above the table — a search box, a filter
        // row. The web puts it in the header; without this it is dropped and
        // the user simply has no way to filter.
        if (node.getHeaderExtra() != null) {
            box.getChildren().add(ctx.render(node.getHeaderExtra()));
        }

        if (!node.getActions().isEmpty()) {
            var toolbar = new HBox(8);
            toolbar.setAlignment(Pos.CENTER_LEFT);
            toolbar.getStyleClass().add("sui-table-actions");
            node.getActions().forEach(a -> toolbar.getChildren().add(ctx.render(a)));
            box.getChildren().add(toolbar);
        }

        var table = buildTable(node, ctx);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().add(table);

        pagination(node, ctx).ifPresent(box.getChildren()::add);
        return box;
    }

    private TableView<UiRow> buildTable(UiTable node, FxRenderContext ctx) {
        var table = new TableView<UiRow>(FXCollections.observableArrayList(node.getRows()));
        // Unconstrained on purpose: the constrained policies force every column
        // into the available width, so a table with more columns than room
        // squeezes them all instead of scrolling — and the row actions on the
        // far right become unreachable. The web scrolls such a table sideways;
        // this policy is what lets the TableView do the same.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        for (UiColumn column : node.getColumns()) {
            table.getColumns().add(dataColumn(column, node, ctx));
        }
        if (!node.getRowActions().isEmpty()) {
            table.getColumns().add(rowActionColumn(node, ctx));
        }

        selection(node, table, ctx);
        sorting(node, table, ctx);
        rowClicks(node, table, ctx);

        if (node.getMaxHeight() != null) {
            parsePx(node.getMaxHeight()).ifPresent(table::setMaxHeight);
        }
        return table;
    }

    private TableColumn<UiRow, String> dataColumn(UiColumn column, UiTable node, FxRenderContext ctx) {
        var label = SuiFxText.first(column.getLabel(), column.getTitle());
        var tc = new TableColumn<UiRow, String>(label == null ? column.getDataKey() : label);
        tc.setId(column.getId());
        tc.setSortable(column.isSortable());
        tc.setCellValueFactory(cell -> {
            var value = cell.getValue().getData().get(column.getDataKey());
            return new SimpleStringProperty(value == null ? "" : value.toString());
        });
        if (column.getCellTemplate() != null) applyCellTemplate(tc, column, ctx);
        return tc;
    }

    /** The trailing column of per-row buttons built from {@link UiTable#getRowActions()}. */
    /**
     * Paints a column's cells from its {@code cellTemplate} instead of as text.
     *
     * <p>A template is one node written once for the whole column, with
     * {@code {dataKey}} placeholders standing in for the row: a link whose
     * href is {@code /workflows/{id}} becomes a different link in every row.
     * Without this the cell fell back to the raw value, so a column meant to
     * be a link showed the id as text and there was no way in.
     */
    private void applyCellTemplate(TableColumn<UiRow, String> tc, UiColumn column, FxRenderContext ctx) {
        tc.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                var row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                var node = forRow(column.getCellTemplate(), row);
                setText(null);
                // Through the renderer, so link, action and text handlers take
                // over and the cell behaves like the node it is.
                setGraphic(node == null ? null : ctx.render(node));
            }
        });
    }

    /**
     * The row's own copy of a cell template.
     *
     * <p>Every string in it is substituted against the row's data plus its id;
     * an unknown placeholder is left as written, so a mistyped key shows up on
     * screen rather than turning into a blank. Ids are suffixed with the row
     * instead of substituted — the same node is painted once per row, and two
     * of them under one id would collide in the render index.
     */
    static UiNode forRow(UiNode template, UiRow row) {
        if (template == null) return null;
        var values = new LinkedHashMap<String, Object>();
        if (row.getData() != null) values.putAll(row.getData());

        var rowId = row.getId() != null ? row.getId()
                : row.getData() == null ? null : row.getData().get("id");
        if (rowId != null) values.put("id", rowId);
        var suffix = rowId == null ? "" : String.valueOf(rowId);

        try {
            return CELL_MAPPER.treeToValue(
                    substitute(CELL_MAPPER.valueToTree(template), values, suffix), UiNode.class);
        } catch (Exception e) {
            // A template that cannot be rebuilt is better skipped than fatal:
            // the rest of the table still comes up.
            return null;
        }
    }

    private static JsonNode substitute(JsonNode node, Map<String, Object> values, String suffix) {
        if (node.isTextual()) return TextNode.valueOf(fill(node.asText(), values));
        if (node.isArray()) {
            var out = CELL_MAPPER.createArrayNode();
            node.forEach(child -> out.add(substitute(child, values, suffix)));
            return out;
        }
        if (!node.isObject()) return node;

        var out = CELL_MAPPER.createObjectNode();
        node.fields().forEachRemaining(field -> {
            var value = field.getValue();
            if ("id".equals(field.getKey()) && value.isTextual() && !suffix.isEmpty()) {
                out.put("id", value.asText() + "__" + suffix);
            } else {
                out.set(field.getKey(), substitute(value, values, suffix));
            }
        });
        return out;
    }

    /** {@code {key}} from {@code values}; unknown keys stay, null becomes empty. */
    private static String fill(String text, Map<String, Object> values) {
        var matcher = PLACEHOLDER.matcher(text);
        var out = new StringBuilder();
        while (matcher.find()) {
            var key = matcher.group(1);
            var replacement = values.containsKey(key)
                    ? (values.get(key) == null ? "" : String.valueOf(values.get(key)))
                    : matcher.group();   // leave a bad key visible rather than blank
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private TableColumn<UiRow, Void> rowActionColumn(UiTable node, FxRenderContext ctx) {
        var tc = new TableColumn<UiRow, Void>("");
        tc.setSortable(false);
        // Wide enough for its buttons and no narrower: this column is the one
        // the user came for, and a default-width column would clip them.
        double width = Math.max(90, 96.0 * node.getRowActions().size());
        tc.setMinWidth(width);
        tc.setPrefWidth(width);
        tc.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                UiRow row = getTableRow().getItem();
                var buttons = new HBox(4);
                buttons.setAlignment(Pos.CENTER_LEFT);
                for (UiAction action : node.getRowActions()) {
                    var button = new Button(SuiFxText.first(action.getLabel(), action.getTitle()));
                    // Carry the action's style, the same class ActionRenderer
                    // uses, so a danger row action reads as one.
                    if (action.getStyle() != null) {
                        button.getStyleClass().add("sui-action-" + action.getStyle().name().toLowerCase());
                    }
                    if (action.getAppearance() != null) {
                        button.getStyleClass().add("sui-action-" + action.getAppearance().name().toLowerCase());
                    }
                    Icons.lead(button, action.getIcon(), ctx);
                    button.setDisable(!action.isEnabled());

                    // One trigger template is shared by every row, so it is
                    // resolved against this row before it is fired.
                    var trigger = Triggers.forRow(action.getOnClick(), row);
                    // The row is the payload source here — a row action acts on
                    // the row it sits in, not on the table.
                    button.setOnAction(e -> {
                        if (trigger == null) return;
                        if (action.getConfirm() != null
                                && !MenuButtonRenderer.confirmed(action.getConfirm())) return;
                        ctx.bus().registerPayloadSource(row.getId(), () -> row.getData());
                        ctx.bus().dispatch(trigger, row, ctx);
                    });
                    buttons.getChildren().add(button);
                }
                setGraphic(buttons);
            }
        });
        return tc;
    }

    private void selection(UiTable node, TableView<UiRow> table, FxRenderContext ctx) {
        var mode = node.getSelectMode() == null ? UiTable.SelectMode.NONE : node.getSelectMode();
        switch (mode) {
            case NONE -> table.setSelectionModel(null);
            case SINGLE -> {
                table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
                select(table, node.getSelectedRowId());
            }
            case MULTI -> {
                table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                node.getSelectedRowIds().forEach(id -> select(table, id));
            }
        }
    }

    private void select(TableView<UiRow> table, String rowId) {
        if (rowId == null) return;
        table.getItems().stream()
                .filter(r -> rowId.equals(r.getId()))
                .findFirst()
                .ifPresent(r -> table.getSelectionModel().select(r));
    }

    /**
     * With a sort trigger the sort is remote: clicking a header dispatches and
     * the reordering arrives with the next patch, so the local sort is vetoed.
     */
    private void sorting(UiTable node, TableView<UiRow> table, FxRenderContext ctx) {
        if (node.getSortTrigger() == null) return;
        table.setSortPolicy(t -> {
            ctx.bus().dispatch(node.getSortTrigger(), node, ctx);
            return false;
        });
    }

    /** A row's own {@code onClick} — rows are UiNodes and can carry one. */
    private void rowClicks(UiTable node, TableView<UiRow> table, FxRenderContext ctx) {
        table.setRowFactory(t -> {
            var row = new javafx.scene.control.TableRow<UiRow>();
            row.setOnMouseClicked(e -> {
                UiRow model = row.getItem();
                if (model == null) return;
                var trigger = e.getClickCount() >= 2 && model.getOnDblClick() != null
                        ? model.getOnDblClick()
                        : model.getOnClick();
                if (trigger == null) return;
                ctx.bus().registerPayloadSource(model.getId(), () -> model.getData());
                ctx.bus().dispatch(trigger, model, ctx);
            });
            return row;
        });
    }

    private java.util.Optional<Node> pagination(UiTable node, FxRenderContext ctx) {
        var page = node.getPagination();
        if (page == null) return java.util.Optional.empty();

        int lastPage = page.getSize() <= 0 ? 0 : (int) ((page.getTotal() - 1) / page.getSize());
        var status = new Label("Page " + (page.getPage() + 1) + " / " + (lastPage + 1)
                + "  (" + page.getTotal() + " rows)");

        var previous = new Button("‹ Previous");
        var next = new Button("Next ›");
        previous.setDisable(page.getPage() <= 0);
        next.setDisable(page.getPage() >= lastPage);

        // The target page goes into the trigger's {page}, which is exactly the
        // place UiTable.Pagination#pageTrigger documents for it.
        var back = Triggers.forPage(page.getPageTrigger(), page.getPage() - 1);
        var forward = Triggers.forPage(page.getPageTrigger(), page.getPage() + 1);
        previous.setOnAction(e -> ctx.bus().dispatch(back, node, ctx));
        next.setOnAction(e -> ctx.bus().dispatch(forward, node, ctx));

        var bar = new HBox(8, previous, next, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("sui-table-pagination");
        return java.util.Optional.of(bar);
    }

    private java.util.Optional<Double> parsePx(String cssLength) {
        try {
            return java.util.Optional.of(Double.parseDouble(cssLength.replaceAll("[^0-9.]", "")));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
}
