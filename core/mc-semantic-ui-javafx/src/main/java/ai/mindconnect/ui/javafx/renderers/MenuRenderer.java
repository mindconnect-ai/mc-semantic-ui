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
import javafx.scene.shape.Rectangle;

/**
 * Paints {@link UiMenu} as a vertical navigation menu.
 *
 * <p>A menu is a list of {@link UiMenuItem}s, each an action: clicking one
 * dispatches its trigger (or navigates its {@code href}). A group — an item
 * with children — becomes a {@link TitledPane} that starts expanded when the
 * model says {@link UiMenuItem#isOpen()}. A badge is a trailing label, a
 * divider a {@link Separator}.
 *
 * <p>{@link UiMenu.State}: {@code EXPANDED} shows the menu, {@code HIDDEN}
 * removes it. <b>{@code RAIL} collapses it away too</b> — see below. The
 * {@code toggle} flag adds a button that flips between the two, client-side,
 * no round trip.
 *
 * <p><b>Why RAIL is not a rail here.</b> On the web a rail is a narrow strip of
 * icons. This renderer has no icons yet ({@link UiMenuItem#getIcon()} resolves
 * through an SVG sprite, which JavaFX has no equivalent for), so a narrow strip
 * would only show truncated labels — a worse thing than either alternative. So
 * RAIL collapses the menu instead. When the menu carries a {@code toggle}, the
 * hamburger button stays behind so it can be opened again; without one, RAIL is
 * simply HIDDEN. Give this a real rail once icons exist.
 *
 * <p>{@link UiMenu.Mode} and {@link UiMenu.Side} are ignored. Both are about how
 * a drawer behaves over a page; a desktop window puts the menu in a layout slot
 * and that is the app's decision, not the menu's.
 */
public class MenuRenderer implements FxNodeRenderer<UiMenu> {

    private static final double EXPANDED_WIDTH = 220;

    @Override
    public Node render(UiMenu node, FxRenderContext ctx) {
        var box = new VBox(2);
        box.setPadding(new Insets(8));

        // Nothing spills past the menu's edge, however long a label gets: clip
        // to the menu's own bounds.
        var clip = new Rectangle();
        clip.widthProperty().bind(box.widthProperty());
        clip.heightProperty().bind(box.heightProperty());
        box.setClip(clip);

        // Everything that collapses lives in its own container, so the toggle
        // can stay visible when the rest is gone — otherwise collapsing would
        // take the toggle with it and the menu could never be reopened.
        var body = new VBox(2);
        if (node.getTitle() != null) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-menu-title");
            body.getChildren().add(title);
        }
        if (node.getItems() != null) {
            node.getItems().forEach(item -> body.getChildren().add(item(item, ctx)));
        }

        boolean hasToggle = Boolean.TRUE.equals(node.getToggle());
        if (hasToggle) box.getChildren().add(toggleButton(box, body));
        box.getChildren().add(body);

        var state = node.getState() == null ? UiMenu.State.EXPANDED : node.getState();
        applyState(box, body, state, hasToggle);
        return box;
    }

    /**
     * EXPANDED shows the body; RAIL and HIDDEN collapse it. The difference
     * between the two is only what is left behind: RAIL keeps the toggle (if
     * there is one) so the menu can come back, HIDDEN takes the whole node out
     * of the layout.
     */
    private void applyState(Region menu, Region body, UiMenu.State state, boolean hasToggle) {
        switch (state) {
            case EXPANDED -> setCollapsed(menu, body, false);
            case RAIL -> {
                setCollapsed(menu, body, true);
                if (!hasToggle) remove(menu);   // nothing left to show
            }
            case HIDDEN -> remove(menu);
        }
    }

    /** Hides or shows the collapsible part, and sizes the menu to match. */
    private void setCollapsed(Region menu, Region body, boolean collapsed) {
        body.setVisible(!collapsed);
        body.setManaged(!collapsed);
        if (collapsed) {
            // Let the menu shrink to whatever is left — the toggle button.
            menu.setPrefWidth(Region.USE_COMPUTED_SIZE);
            menu.setMinWidth(Region.USE_COMPUTED_SIZE);
            menu.setMaxWidth(Region.USE_COMPUTED_SIZE);
        } else {
            menu.setPrefWidth(EXPANDED_WIDTH);
            menu.setMinWidth(EXPANDED_WIDTH);
            menu.setMaxWidth(EXPANDED_WIDTH);
        }
    }

    private void remove(Region menu) {
        menu.setVisible(false);
        menu.setManaged(false);
    }

    /** Flips the menu open/collapsed. Pure client state — no trigger involved. */
    private Button toggleButton(Region menu, Region body) {
        var toggle = new Button("☰");
        toggle.getStyleClass().add("sui-menu-toggle");
        toggle.setOnAction(e -> setCollapsed(menu, body, body.isVisible()));
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
            // A TitledPane's min width is its title + arrow, which a long group
            // title pushes past the menu's own width — and when min > max, min
            // wins, so it would overflow. Release the min so it shrinks to the
            // menu instead, truncating the title.
            group.setMinWidth(0);
            group.setMaxWidth(Double.MAX_VALUE);
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
        badge.setMinWidth(Region.USE_PREF_SIZE);   // the count stays whole; the label gives way
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(button, Priority.ALWAYS);
        var row = new HBox(4, button, spacer, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        // Take the menu's width, not the row's preferred one, so the button
        // truncates and the badge stays inside instead of spilling out.
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private String label(UiMenuItem item) {
        return item.getLabel() != null ? item.getLabel() : item.getTitle();
    }
}
