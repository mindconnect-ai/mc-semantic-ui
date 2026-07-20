package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiProgress;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;

/**
 * Paints {@link UiProgress} as a {@link ProgressBar}, or a circular
 * {@link ProgressIndicator} for the {@code CIRCLE} variant.
 *
 * <p>No value means indeterminate — {@link UiProgress#indeterminate()} maps
 * straight onto JavaFX's own indeterminate mode. With a value, the fraction is
 * {@code value / max} (max defaults to 1, so a plain {@code of(0.4)} is 40%),
 * and {@code showValue} appends the percentage as text.
 *
 * <p>{@link UiProgress.Status} becomes a style class
 * ({@code sui-progress-error} and friends) rather than a hardcoded colour, so
 * a theme decides what "warning" looks like.
 */
public class ProgressRenderer implements FxNodeRenderer<UiProgress> {

    @Override
    public Node render(UiProgress node, FxRenderContext ctx) {
        double max = node.getMax() == null || node.getMax() == 0 ? 1 : node.getMax();
        double fraction = node.getValue() == null
                ? ProgressIndicator.INDETERMINATE_PROGRESS
                : clamp(node.getValue() / max);

        var control = node.getVariant() == UiProgress.Variant.CIRCLE
                ? new ProgressIndicator(fraction)
                : new ProgressBar(fraction);

        if (node.getStatus() != null) {
            control.getStyleClass().add("sui-progress-" + node.getStatus().name().toLowerCase());
        }
        if (control instanceof ProgressBar bar) {
            bar.setMaxWidth(Double.MAX_VALUE);
        }

        if (!Boolean.TRUE.equals(node.getShowValue()) || node.getValue() == null) {
            return control;
        }

        var text = new Label(Math.round(clamp(node.getValue() / max) * 100) + "%");
        text.getStyleClass().add("sui-progress-value");
        var row = new HBox(8, control, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static double clamp(double fraction) {
        return Math.max(0, Math.min(1, fraction));
    }
}
