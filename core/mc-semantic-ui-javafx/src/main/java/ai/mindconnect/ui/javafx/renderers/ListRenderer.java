package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiList} as a stack of rows.
 *
 * <p>Not a JavaFX {@code ListView}: a {@link UiList.Item} is a small layout of
 * its own — label, description, its own actions, optionally an expandable body
 * — and cell virtualisation would fight that for no gain at the sizes a
 * semantic list is meant for. A collapsible item becomes a
 * {@link TitledPane}, the same role {@code <details>} plays on the web.
 *
 * <p>An item with an {@code onClick} makes the whole row clickable, with the
 * item's own actions taking precedence — the nearest handler wins, as
 * everywhere else in the vocabulary.
 */
public class ListRenderer implements FxNodeRenderer<UiList> {

    @Override
    public Node render(UiList node, FxRenderContext ctx) {
        var box = new VBox(8);

        if (node.getTitle() != null) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-list-title");
            box.getChildren().add(title);
        }

        if (!node.getActions().isEmpty()) {
            var toolbar = new HBox(8);
            toolbar.setAlignment(Pos.CENTER_LEFT);
            node.getActions().forEach(a -> toolbar.getChildren().add(ctx.render(a)));
            box.getChildren().add(toolbar);
        }

        var items = new VBox(4);
        items.getStyleClass().add("sui-list-items");
        node.getItems().forEach(item -> items.getChildren().add(item(item, ctx)));
        box.getChildren().add(items);

        pagination(node, ctx).ifPresent(box.getChildren()::add);
        return box;
    }

    private Node item(UiList.Item item, FxRenderContext ctx) {
        var body = new VBox(4);
        body.getStyleClass().add("sui-list-item");
        body.setPadding(new Insets(8));

        body.getChildren().add(header(item, ctx));

        if (item.getDescription() != null) {
            var description = new Label(item.getDescription());
            description.getStyleClass().add("sui-list-description");
            description.setWrapText(true);
            body.getChildren().add(description);
        }
        if (item.getContent() != null) {
            body.getChildren().add(ctx.render(item.getContent()));
        }

        if (item.getOnClick() != null) {
            body.getStyleClass().add("sui-clickable");
            // A list Item is not a UiNode, so there is no source node to hand
            // over — which is why item triggers carry their identity in the
            // trigger itself (a url, or an invoke payload id), the same way
            // they do on the web.
            body.setOnMouseClicked(e -> {
                e.consume();
                ctx.bus().dispatch(item.getOnClick(), null, ctx);
            });
        }

        if (item.getCollapseSummary() == null) return body;

        // Collapsible: the summary is the header, the rest is the body.
        var pane = new TitledPane(item.getCollapseSummary(), body);
        pane.setExpanded(item.isCollapseOpen());
        pane.getStyleClass().add("sui-list-collapsible");
        return pane;
    }

    /** Label (or label node) on the left, the item's own actions on the right. */
    private Node header(UiList.Item item, FxRenderContext ctx) {
        var row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Node label = item.getLabelNode() != null
                ? ctx.render(item.getLabelNode())
                : new Label(item.getLabel() == null ? "" : item.getLabel());
        if (label instanceof Label text) text.getStyleClass().add("sui-list-label");
        row.getChildren().add(label);

        if (item.getActions().isEmpty()) return row;

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);
        item.getActions().forEach(a -> row.getChildren().add(ctx.render(a)));
        return row;
    }

    private java.util.Optional<Node> pagination(UiList node, FxRenderContext ctx) {
        var page = node.getPagination();
        if (page == null) return java.util.Optional.empty();

        int lastPage = page.getSize() <= 0 ? 0 : (int) ((page.getTotal() - 1) / page.getSize());
        var status = new Label("Page " + (page.getPage() + 1) + " / " + (lastPage + 1)
                + "  (" + page.getTotal() + " items)");

        var previous = new Button("‹ Previous");
        var next = new Button("Next ›");
        previous.setDisable(page.getPage() <= 0);
        next.setDisable(page.getPage() >= lastPage);
        // Same limitation as the table's pager: the trigger carries no target
        // page, so both buttons fire it as modelled.
        previous.setOnAction(e -> ctx.bus().dispatch(page.getPageTrigger(), node, ctx));
        next.setOnAction(e -> ctx.bus().dispatch(page.getPageTrigger(), node, ctx));

        var bar = new HBox(8, previous, next, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("sui-list-pagination");
        return java.util.Optional.of(bar);
    }
}
