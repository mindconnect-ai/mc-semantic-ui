package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.model.UiAction;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

/**
 * Paints {@link UiAction} as a {@link Button} — or a {@link Hyperlink} for the
 * {@code LINK} appearance. Style, disabled state and the confirm prompt follow
 * the same rules as the web renderers.
 */
public class ActionRenderer implements FxNodeRenderer<UiAction> {

    @Override
    public Node render(UiAction node, FxRenderContext ctx) {
        var label = node.getLabel() != null ? node.getLabel() : node.getTitle();
        ButtonBase button = node.getAppearance() == UiAction.Appearance.LINK
                ? new Hyperlink(label)
                : new Button(label);

        if (node.getStyle() != null) {
            button.getStyleClass().add("sui-action-" + node.getStyle().name().toLowerCase());
        }
        if (node.getAppearance() != null) {
            button.getStyleClass().add("sui-action-" + node.getAppearance().name().toLowerCase());
        }

        button.setDisable(!node.isEnabled() || node.isLoading());
        if (node.isLoading()) {
            // The declarative busy state: the model says this action is already
            // running. Same style class the bus puts on a control while its own
            // trigger is in flight, so both look identical.
            button.getStyleClass().add(SuiFxEventBus.LOADING_CLASS);
            button.setGraphic(spinner());
        }
        if (!node.isEnabled() && node.getDisabledReason() != null) {
            // A disabled JavaFX control swallows hover events, so the tooltip
            // goes on a wrapper-free control via the tooltip API on the parent
            // skin; setting it here still works for enabled→disabled toggles.
            Tooltip.install(button, new Tooltip(node.getDisabledReason()));
        }

        button.setOnAction(e -> {
            if (node.getOnClick() == null) return;
            if (node.getConfirm() != null && !confirmed(node.getConfirm())) return;
            ctx.bus().dispatch(node.getOnClick(), node, ctx);
        });
        // The button owns its click; keep SuiFxRenderer from wiring a second
        // handler for the same trigger.
        button.getProperties().put(SuiFxEventBus.CLICK_HANDLED_KEY, Boolean.TRUE);

        return button;
    }

    /** The small inline spinner shown inside a declaratively-loading button. */
    private ProgressIndicator spinner() {
        var indicator = new ProgressIndicator();
        indicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        indicator.setPrefSize(14, 14);
        indicator.setMaxSize(14, 14);
        return indicator;
    }

    private boolean confirmed(String message) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
