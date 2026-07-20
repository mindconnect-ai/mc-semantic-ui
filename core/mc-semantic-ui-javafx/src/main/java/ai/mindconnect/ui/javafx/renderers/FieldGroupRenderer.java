package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiFieldGroup;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiFieldGroup} as a titled block of fields. Stays inside the
 * enclosing form scope, so grouped fields submit with everything else.
 */
public class FieldGroupRenderer implements FxNodeRenderer<UiFieldGroup> {

    @Override
    public Node render(UiFieldGroup node, FxRenderContext ctx) {
        var box = new VBox(8);

        if (node.getTitle() != null) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-fieldgroup-title");
            box.getChildren().add(title);
        }
        if (node.getHint() != null) {
            var hint = new Label(node.getHint());
            hint.getStyleClass().add("sui-fieldgroup-hint");
            hint.setWrapText(true);
            box.getChildren().add(hint);
        }

        node.getContent().forEach(child -> box.getChildren().add(ctx.render(child)));
        return box;
    }
}
