package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiText;
import javafx.scene.Node;
import javafx.scene.control.Label;

/** Paints {@link UiText} as a wrapping {@link Label}. */
public class TextRenderer implements FxNodeRenderer<UiText> {

    @Override
    public Node render(UiText node, FxRenderContext ctx) {
        var text = node.getText() != null ? node.getText() : node.getTitle();
        var label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        return label;
    }
}
