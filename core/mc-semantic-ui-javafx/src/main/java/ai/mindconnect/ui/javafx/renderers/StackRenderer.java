package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiStack;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiStack} as an {@link HBox} or {@link VBox}. Vertical is the
 * default, matching the web renderers.
 */
public class StackRenderer implements FxNodeRenderer<UiStack> {

    static final int DEFAULT_GAP = 8;

    @Override
    public Node render(UiStack node, FxRenderContext ctx) {
        int gap = node.getGap() == null ? DEFAULT_GAP : node.getGap();
        Pane pane;
        if (node.getDirection() == UiStack.Direction.HORIZONTAL) {
            var hbox = new HBox(gap);
            hbox.setAlignment(Pos.CENTER_LEFT);
            pane = hbox;
        } else {
            pane = new VBox(gap);
        }
        node.getChildren().forEach(child -> pane.getChildren().add(ctx.render(child)));
        return pane;
    }
}
