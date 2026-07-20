package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiSpinner;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;

/**
 * Paints {@link UiSpinner} as an indeterminate {@link ProgressIndicator},
 * optionally followed by its label.
 *
 * <p>This is the <em>declarative</em> spinner — a node the model puts in the
 * tree to say "this part is still loading". Not to be confused with the busy
 * indicator around a dispatch, which is
 * {@link ai.mindconnect.ui.javafx.SuiFxOverlay}'s job.
 */
public class SpinnerRenderer implements FxNodeRenderer<UiSpinner> {

    @Override
    public Node render(UiSpinner node, FxRenderContext ctx) {
        var indicator = new ProgressIndicator();
        indicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        double size = switch (node.getSize() == null ? UiSpinner.Size.MD : node.getSize()) {
            case SM -> 16;
            case MD -> 28;
            case LG -> 48;
        };
        indicator.setPrefSize(size, size);
        indicator.setMinSize(size, size);
        indicator.setMaxSize(size, size);

        var label = node.getLabel() != null ? node.getLabel() : node.getTitle();
        if (label == null) return indicator;

        var row = new HBox(8, indicator, new Label(label));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
