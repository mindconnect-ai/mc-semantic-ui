package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.javafx.SuiFxText;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiActionMenu;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;

/**
 * A {@link UiActionMenu} — a button that opens a menu, in a form's button
 * bar — as a JavaFX {@link MenuButton} wearing the same style classes as the
 * bar's other buttons ({@code sui-action-secondary}, …).
 *
 * <p>The entries are {@link MenuButtonRenderer}'s, painted with the context
 * this renderer was given — inside a form that is the form's, so an entry
 * whose trigger names no payload sends the form's fields, like any button of
 * the form, and one that names the form finds it by id. The popup is
 * JavaFX's own: it opens above the button when there is no room below, stays
 * on screen, and is driven by the keyboard (Space / Enter / ↓ to open,
 * arrows to move, Esc to close).
 */
public class ActionMenuRenderer implements FxNodeRenderer<UiActionMenu> {

    @Override
    public Node render(UiActionMenu node, FxRenderContext ctx) {
        var button = new MenuButton(SuiFxText.first(node.getLabel(), node.getTitle()));
        var style = node.getStyle() != null ? node.getStyle() : UiAction.Style.SECONDARY;
        button.getStyleClass().addAll("sui-action-" + style.name().toLowerCase(), "sui-action-button", "sui-action-menu");
        Icons.lead(button, node.getIcon(), ctx);

        button.setDisable(!node.isEnabled() || node.isLoading());
        if (!node.isEnabled() && node.getDisabledReason() != null) {
            Tooltip.install(button, new Tooltip(node.getDisabledReason()));
        }
        if (node.getItems() != null) {
            node.getItems().forEach(item -> button.getItems().add(MenuButtonRenderer.menuItem(item, ctx)));
        }
        // The button opens its menu; the entries act. Nothing for the generic
        // click wiring to add.
        button.getProperties().put(SuiFxEventBus.CLICK_HANDLED_KEY, Boolean.TRUE);
        return button;
    }
}
