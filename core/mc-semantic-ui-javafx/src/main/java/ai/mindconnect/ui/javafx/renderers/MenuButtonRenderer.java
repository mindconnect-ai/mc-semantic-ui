package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxText;
import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.model.UiMenuButton;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiTrigger;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * Paints {@link UiMenuButton} as a {@link MenuButton} — the overflow / "…"
 * menu of the vocabulary.
 *
 * <p>On the web this needs a popover enhancer; JavaFX has the control, so the
 * whole thing is a straight mapping: items become {@link MenuItem}s, an item
 * with children becomes a submenu, {@link UiMenuItem#isDivider()} a separator.
 *
 * <p>Items are {@link ai.mindconnect.ui.model.UiAction}s, so they carry the
 * same {@code confirm}, {@code enabled} and trigger semantics — an item with
 * an {@code href} navigates, one with an {@code onClick} dispatches it.
 */
public class MenuButtonRenderer implements FxNodeRenderer<UiMenuButton> {

    @Override
    public Node render(UiMenuButton node, FxRenderContext ctx) {
        var label = SuiFxText.first(node.getLabel(), node.getTitle());
        var glyph = ctx.icon(node.getIcon());
        // The ICON variant carries no label on the web either — it is the glyph.
        // The ellipsis is the last resort, for a button with neither.
        var button = new MenuButton(label != null ? label : glyph != null ? "" : "…");
        if (glyph != null) button.setGraphic(glyph.inherit(button));

        if (node.getVariant() != null) {
            button.getStyleClass().add("sui-menu-button-" + node.getVariant().name().toLowerCase());
        }
        if (node.getItems() != null) {
            node.getItems().forEach(item -> button.getItems().add(menuItem(item, ctx)));
        }
        button.getProperties().put(SuiFxEventBus.CLICK_HANDLED_KEY, Boolean.TRUE);
        return button;
    }

    /** One model item → one JavaFX item, recursing into groups. */
    static MenuItem menuItem(UiMenuItem item, FxRenderContext ctx) {
        if (item.isDivider()) return new SeparatorMenuItem();

        var label = SuiFxText.first(item.getLabel(), item.getTitle());

        if (item.getChildren() != null && !item.getChildren().isEmpty()) {
            var submenu = new Menu(label);
            Icons.lead(submenu, item.getIcon(), ctx);
            item.getChildren().forEach(child -> submenu.getItems().add(menuItem(child, ctx)));
            return submenu;
        }

        var menuItem = new MenuItem(label);
        Icons.lead(menuItem, item.getIcon(), ctx);
        menuItem.setId(item.getId());
        menuItem.setDisable(!item.isEnabled());
        if (item.isDanger()) menuItem.getStyleClass().add("sui-menu-item-danger");
        if (item.isSelected()) menuItem.getStyleClass().add("sui-menu-item-selected");

        menuItem.setOnAction(e -> {
            var trigger = triggerFor(item);
            if (trigger == null) return;
            if (item.getConfirm() != null && !confirmed(item.getConfirm())) return;
            ctx.bus().dispatch(trigger, item, ctx);
        });
        return menuItem;
    }

    static UiTrigger triggerFor(UiMenuItem item) {
        if (item.getOnClick() != null) return item.getOnClick();
        return item.getHref() == null ? null : UiTrigger.go(item.getHref());
    }

    static boolean confirmed(String message) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
