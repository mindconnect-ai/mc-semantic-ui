package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiToast;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The chrome that lives <em>above</em> the rendered tree: transient toasts in
 * the top-right corner and the global busy indicator.
 *
 * <p>Neither belongs in the {@code UiNode} tree — a toast is not a node, and
 * the busy state is a property of a dispatch, not of the model. So they get a
 * layer of their own that the app wraps around the mounted content:
 *
 * <pre>{@code
 * var overlay = new SuiFxOverlay(bus.mount(tree));
 * bus.setOverlay(overlay);            // toasts and busy state now land here
 * stage.setScene(new Scene(overlay, 900, 640));
 * }</pre>
 *
 * <p>The busy scrim appears only after {@link #BUSY_DELAY}. A local
 * {@code INVOKE} handler usually finishes in microseconds, and flashing a
 * modal scrim for that reads as a glitch — so fast work stays invisible and
 * only genuinely slow work announces itself.
 */
public class SuiFxOverlay extends StackPane {

    /** How long a dispatch may take before the scrim shows up. */
    public static final Duration BUSY_DELAY = Duration.millis(250);

    private final VBox toasts = new VBox(8);
    private final StackPane scrim = new StackPane(new ProgressIndicator());
    private final PauseTransition busyDelay = new PauseTransition(BUSY_DELAY);
    private int busyCount;

    public SuiFxOverlay(Node content) {
        getStyleClass().add("sui-overlay");

        toasts.setAlignment(Pos.TOP_RIGHT);
        toasts.setPadding(new Insets(16));
        toasts.setPickOnBounds(false);
        toasts.setMouseTransparent(false);
        StackPane.setAlignment(toasts, Pos.TOP_RIGHT);

        scrim.getStyleClass().add("sui-busy-scrim");
        scrim.setVisible(false);
        busyDelay.setOnFinished(e -> scrim.setVisible(busyCount > 0));

        getChildren().addAll(content, scrim, toasts);
    }

    // ── toasts ────────────────────────────────────────────────────────────

    /**
     * Shows a toast card. It removes itself after
     * {@link UiToast#getDurationMs()}; a duration of {@code 0} makes it sticky
     * and gives it a close button instead.
     */
    public void toast(UiToast toast) {
        if (toast == null) return;

        var level = toast.getLevel() == null ? UiToast.Level.INFO : toast.getLevel();
        var card = new VBox(2);
        card.getStyleClass().addAll("sui-toast", "sui-toast-" + level.name().toLowerCase());
        card.setMaxWidth(360);

        if (toast.getTitle() != null) {
            var title = new Label(toast.getTitle());
            title.getStyleClass().add("sui-toast-title");
            title.setWrapText(true);
            card.getChildren().add(title);
        }
        if (toast.getMessage() != null) {
            var message = new Label(toast.getMessage());
            message.getStyleClass().add("sui-toast-message");
            message.setWrapText(true);
            card.getChildren().add(message);
        }

        if (toast.getDurationMs() <= 0) {
            var close = new Button("Dismiss");
            close.getStyleClass().add("sui-toast-close");
            close.setOnAction(e -> toasts.getChildren().remove(card));
            var row = new HBox(close);
            row.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(row);
        } else {
            var life = new PauseTransition(Duration.millis(toast.getDurationMs()));
            life.setOnFinished(e -> toasts.getChildren().remove(card));
            life.play();
        }

        toasts.getChildren().add(card);
    }

    /** Removes every visible toast. */
    public void clearToasts() {
        toasts.getChildren().clear();
    }

    /** The toast container, for apps that want to restyle or reposition it. */
    public Pane toastPane() {
        return toasts;
    }

    // ── busy ──────────────────────────────────────────────────────────────

    /**
     * Raises or lowers the busy state. Calls nest: two overlapping dispatches
     * mean the scrim only goes away once both are done.
     */
    public void setBusy(boolean busy) {
        busyCount = Math.max(0, busyCount + (busy ? 1 : -1));
        if (busyCount > 0) {
            busyDelay.playFromStart();
        } else {
            busyDelay.stop();
            scrim.setVisible(false);
        }
    }

    /** Whether at least one dispatch is currently in flight. */
    public boolean isBusy() {
        return busyCount > 0;
    }
}
