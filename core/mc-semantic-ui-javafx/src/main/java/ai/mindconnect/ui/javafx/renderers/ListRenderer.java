package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxText;
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

        if (SuiFxText.present(node.getTitle())) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-list-title");
            Icons.lead(title, node.getIcon(), ctx);
            box.getChildren().add(title);
        }

        // Whatever the server wants above the list — a search box, a filter
        // row. Dropped before, which left a page with no way to filter at all.
        if (node.getHeaderExtra() != null) {
            box.getChildren().add(ctx.render(node.getHeaderExtra()));
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

        var head = header(item, ctx);
        if (head != null) body.getChildren().add(head);

        if (SuiFxText.present(item.getDescription())) {
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

        if (!SuiFxText.present(item.getCollapseSummary())) return body;

        // Collapsible: the summary is the header, the rest is the body.
        var pane = new TitledPane(item.getCollapseSummary(), body);
        pane.setExpanded(item.isCollapseOpen());
        pane.getStyleClass().add("sui-list-collapsible");
        return pane;
    }

    /**
     * Label (or label node) on the left, the item's own actions on the right.
     *
     * @return {@code null} when there is nothing to put in it. A collapsible
     *         item carries its text in the collapse summary and sends an empty
     *         label, and a row built for that would be a blank strip above the
     *         content — which is exactly what the browser does not draw.
     */
    private Node header(UiList.Item item, FxRenderContext ctx) {
        boolean hasLabel = item.getLabelNode() != null || SuiFxText.present(item.getLabel());
        if (!hasLabel && item.getActions().isEmpty()) return null;

        var row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        if (hasLabel) {
            Node label = item.getLabelNode() != null
                    ? ctx.render(item.getLabelNode())
                    : new Label(item.getLabel());
            if (label instanceof Label text) {
                text.getStyleClass().add("sui-list-label");
                // Only the plain label carries the glyph: a labelNode is the
                // caller's own layout and stays untouched.
                Icons.lead(text, item.getIcon(), ctx);
            }
            row.getChildren().add(label);
        }

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
        // The target page goes into the trigger's {page}, which is exactly the
        // place UiList.Pagination#pageTrigger documents for it.
        var back = Triggers.forPage(page.getPageTrigger(), page.getPage() - 1);
        var forward = Triggers.forPage(page.getPageTrigger(), page.getPage() + 1);
        previous.setOnAction(e -> ctx.bus().dispatch(back, node, ctx));
        next.setOnAction(e -> ctx.bus().dispatch(forward, node, ctx));

        var bar = new HBox(8, previous, next, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("sui-list-pagination");
        return java.util.Optional.of(bar);
    }
}
