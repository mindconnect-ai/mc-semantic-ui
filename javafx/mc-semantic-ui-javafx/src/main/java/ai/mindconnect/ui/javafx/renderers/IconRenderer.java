package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiIcon;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * Paints {@link UiIcon} — a standalone icon, as opposed to the leading glyphs
 * the other renderers hang off their own controls.
 *
 * <p>The glyph goes in a {@link Label} rather than into the tree bare, so it
 * inherits colour and size the way the web icon inherits {@code currentColor}
 * and {@code 1em}: the stylesheet talks to the label, and the icon follows it.
 *
 * <p>An unknown token paints nothing. A typo should cost a glyph, not the
 * screen it sits on — the same call the web resolver makes.
 */
public class IconRenderer implements FxNodeRenderer<UiIcon> {

    @Override
    public Node render(UiIcon node, FxRenderContext ctx) {
        var label = new Label();
        label.getStyleClass().add("sui-icon-node");

        var icon = ctx.icon(node.getName());
        if (icon != null) label.setGraphic(icon.inherit(label));

        // UiIcon.labelled() is the accessible name; without one the icon is
        // decorative, which is aria-hidden on the web and simply unnamed here.
        if (node.getTitle() != null && !node.getTitle().isBlank()) {
            label.setAccessibleText(node.getTitle());
            Tooltip.install(label, new Tooltip(node.getTitle()));
        }
        return label;
    }
}
