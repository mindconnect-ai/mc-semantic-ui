package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiMenuItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiMenu} as a vertical navigation rail.
 *
 * <p>A menu is a list of {@link UiMenuItem}s, each an action: clicking one
 * dispatches its trigger (or navigates its {@code href}). A group — an item
 * with children — becomes a {@link TitledPane} that starts expanded when the
 * model says {@link UiMenuItem#isOpen()}. A badge is a trailing label, a
 * divider a {@link Separator}.
 *
 * <p>{@link UiMenu.State} maps onto width: {@code EXPANDED} shows labels,
 * {@code RAIL} narrows to a strip, {@code HIDDEN} collapses the menu away. The
 * {@code toggle} flag adds a button that flips between expanded and rail —
 * client-side state, no round trip, exactly like the web enhancer.
 *
 * <p>First draft: {@link UiMenu.Mode} and {@link UiMenu.Side} are ignored. Both
 * are about how a drawer behaves over a page; a desktop window puts the menu
 * in a layout slot and that is the app's decision, not the menu's.
 */
public class MenuRenderer implements FxNodeRenderer<UiMenu> {

    private static final double RAIL_WIDTH = 56;
    private static final double EXPANDED_WIDTH = 220;

    @Override
    public Node render(UiMenu node, FxRenderContext ctx) {
        var box = new VBox(2);
        box.setPadding(new Insets(8));

        var state = node.getState() == null ? UiMenu.State.EXPANDED : node.getState();
        applyState(box, state);

        if (Boolean.TRUE.equals(node.getToggle())) {
            box.getChildren().add(toggleButton(box));
        }
        if (node.getTitle() != null) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-menu-title");
            box.getChildren().add(title);
        }
        if (node.getItems() != null) {
            node.getItems().forEach(item -> box.getChildren().add(item(item, ctx)));
        }
        return box;
    }

    /** The width/visibility side of {@link UiMenu.State}. */
    private void applyState(Region menu, UiMenu.State state) {
        switch (state) {
            case HIDDEN -> {
                menu.setVisible(false);
                menu.setManaged(false);
            }
            case RAIL -> setWidth(menu, RAIL_WIDTH);
            case EXPANDED -> setWidth(menu, EXPANDED_WIDTH);
        }
    }

    private void setWidth(Region menu, double width) {
        menu.setPrefWidth(width);
        menu.setMinWidth(width);
        menu.setMaxWidth(width);
    }

    /** Flips between expanded and rail. Pure client state — no trigger involved. */
    private Button toggleButton(Region menu) {
        var toggle = new Button("☰");
        toggle.getStyleClass().add("sui-menu-toggle");
        toggle.setOnAction(e -> setWidth(menu,
                menu.getPrefWidth() == EXPANDED_WIDTH ? RAIL_WIDTH : EXPANDED_WIDTH));
        return toggle;
    }

    private Node item(UiMenuItem item, FxRenderContext ctx) {
        if (item.isDivider()) return new Separator();

        if (item.getChildren() != null && !item.getChildren().isEmpty()) {
            var children = new VBox(2);
            item.getChildren().forEach(child -> children.getChildren().add(item(child, ctx)));
            var group = new TitledPane(label(item), children);
            group.setExpanded(item.isOpen());
            group.getStyleClass().add("sui-menu-group");
            return group;
        }

        var button = new Button(label(item));
        button.getStyleClass().add("sui-menu-item");
        if (item.isSelected()) button.getStyleClass().add("sui-menu-item-selected");
        if (item.isDanger()) button.getStyleClass().add("sui-menu-item-danger");
        button.setDisable(!item.isEnabled());
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);

        button.setOnAction(e -> {
            var trigger = MenuButtonRenderer.triggerFor(item);
            if (trigger == null) return;
            if (item.getConfirm() != null && !MenuButtonRenderer.confirmed(item.getConfirm())) return;
            ctx.bus().dispatch(trigger, item, ctx);
        });

        if (item.getBadge() == null) return button;

        var badge = new Label(item.getBadge());
        badge.getStyleClass().add("sui-menu-badge");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(button, Priority.ALWAYS);
        var row = new HBox(4, button, spacer, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String label(UiMenuItem item) {
        return item.getLabel() != null ? item.getLabel() : item.getTitle();
    }
}
