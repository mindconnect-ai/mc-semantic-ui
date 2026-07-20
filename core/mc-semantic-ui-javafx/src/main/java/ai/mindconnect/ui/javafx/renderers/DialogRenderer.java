package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiDialog;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiDialog} as a titled panel.
 *
 * <p>Deliberately <em>not</em> a window. A dialog reached through the model —
 * patched into the tree, or arriving in a response — is a region of the UI,
 * and painting it as a Node keeps it patchable and testable like everything
 * else. To actually pop it up as a modal window, hand it to
 * {@link ai.mindconnect.ui.javafx.SuiFxEventBus#showDialog(UiDialog)}, which
 * paints it with this very renderer and puts the result in a modal stage.
 *
 * <p>{@link UiDialog#getCloseHref()} is honoured by {@code showDialog} (it
 * closes the window and dispatches the href); inline, there is nothing to
 * close, so it is ignored.
 */
public class DialogRenderer implements FxNodeRenderer<UiDialog> {

    @Override
    public Node render(UiDialog node, FxRenderContext ctx) {
        var box = new VBox(12);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("sui-dialog-panel");

        if (node.getTitle() != null) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-dialog-title");
            box.getChildren().add(title);
        }
        if (node.getNode() != null) {
            box.getChildren().add(ctx.render(node.getNode()));
        }
        return box;
    }
}
