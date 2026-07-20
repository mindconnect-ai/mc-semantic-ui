package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiTree;
import ai.mindconnect.ui.model.UiTreeNode;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;

/**
 * Paints {@link UiTree} as a {@link TreeView}.
 *
 * <p>The model's own state drives the widget: {@link UiTreeNode#isOpen()}
 * expands a branch, {@link UiTreeNode#isSelected()} selects it. Selecting a
 * node dispatches its {@code onClick} — which is how a tree drives a detail
 * pane, exactly as in the web renderers.
 *
 * <p>A {@link UiTreeNode#getLabelNode()} is rendered through the normal
 * renderer chain and used as the cell's graphic, so a tree label can be any
 * node (a text with an icon, a badge, …). Plain {@code label} text is the
 * common case and stays cheap.
 *
 * <p>First draft: {@link UiTreeNode#getIcon()} is ignored — icons resolve
 * through an SVG sprite on the web, which has no JavaFX equivalent yet.
 */
public class TreeRenderer implements FxNodeRenderer<UiTree> {

    @Override
    public Node render(UiTree node, FxRenderContext ctx) {
        // An invisible root keeps the model's top level as the visible top
        // level — UiTree has a list of roots, TreeView insists on exactly one.
        var root = new TreeItem<UiTreeNode>();
        var tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setCellFactory(v -> new UiTreeCell(ctx));

        node.getNodes().forEach(child -> root.getChildren().add(item(child, tree)));

        tree.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> {
            if (selected == null || selected.getValue() == null) return;
            var model = selected.getValue();
            if (model.getOnClick() != null) ctx.bus().dispatch(model.getOnClick(), model, ctx);
        });

        if (node.getTitle() == null) return tree;

        var title = new Label(node.getTitle());
        title.getStyleClass().add("sui-tree-title");
        return new VBox(4, title, tree);
    }

    /** Builds the item for one model node and, recursively, its children. */
    private TreeItem<UiTreeNode> item(UiTreeNode model, TreeView<UiTreeNode> tree) {
        var item = new TreeItem<>(model);
        item.setExpanded(model.isOpen());
        model.getChildren().forEach(child -> item.getChildren().add(item(child, tree)));
        if (model.isSelected()) {
            // Selecting during construction would fire the listener before the
            // tree is even shown; defer it to the next pulse.
            javafx.application.Platform.runLater(() -> tree.getSelectionModel().select(item));
        }
        return item;
    }

    /** Renders a node's label — text, or a full UiNode when one is given. */
    private static class UiTreeCell extends TreeCell<UiTreeNode> {

        private final FxRenderContext ctx;

        UiTreeCell(FxRenderContext ctx) {
            this.ctx = ctx;
        }

        @Override
        protected void updateItem(UiTreeNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (item.getLabelNode() != null) {
                setText(null);
                setGraphic(ctx.render(item.getLabelNode()));
            } else {
                setGraphic(null);
                setText(item.getLabel() != null ? item.getLabel() : item.getTitle());
            }
        }
    }
}
