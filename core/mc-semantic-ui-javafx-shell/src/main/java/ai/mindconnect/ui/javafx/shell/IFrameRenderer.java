package ai.mindconnect.ui.javafx.shell;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiIFrame;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * Paints {@link UiIFrame} as a {@link WebView} — the desktop's embedded
 * browsing context.
 *
 * <p>{@code height} takes pixels. A viewport-relative length like {@code 60vh}
 * has no JavaFX equivalent, so the view stays uncapped and a grow hint gives it
 * whatever the parent column has left — the same call
 * {@code ScrollPaneRenderer} makes, and for the same reason.
 *
 * <p>{@code sandbox} is the one place the desktop cannot follow the browser.
 * The attribute is a list of permissions the HTML spec defines for an
 * {@code <iframe>}; a WebView implements none of that vocabulary and grants a
 * page the run of its own engine either way. Rather than pretend, the renderer
 * honours the only distinction it can actually enforce — {@code sandbox} with
 * no {@code allow-scripts} turns JavaScript off — and leaves the rest to the
 * caller. Do not treat a WebView as a security boundary: point it at content
 * you trust.
 */
public class IFrameRenderer implements FxNodeRenderer<UiIFrame> {

    /** The sandbox token that keeps scripts running; its absence is the one rule we can enforce. */
    private static final String ALLOW_SCRIPTS = "allow-scripts";

    @Override
    public Node render(UiIFrame node, FxRenderContext ctx) {
        var view = new WebView();
        view.getStyleClass().add("sui-iframe");

        if (node.getSandbox() != null && !node.getSandbox().contains(ALLOW_SCRIPTS)) {
            view.getEngine().setJavaScriptEnabled(false);
        }
        if (node.getSrc() != null && !node.getSrc().isBlank()) {
            view.getEngine().load(node.getSrc());
        }
        if (node.getTitle() != null) {
            view.setAccessibleText(node.getTitle());
        }

        double capped = pixels(node.getHeight());
        if (capped > 0) {
            view.setPrefHeight(capped);
            view.setMinHeight(capped);
            view.setMaxHeight(capped);
        } else {
            view.setPrefHeight(Region.USE_COMPUTED_SIZE);
            VBox.setVgrow(view, Priority.ALWAYS);
        }
        return view;
    }

    /**
     * A CSS length in pixels, or 0 when it is anything JavaFX cannot place on a
     * pixel grid — {@code 60vh}, {@code 100%}, {@code auto}.
     */
    static double pixels(String length) {
        if (length == null || length.isBlank()) return 0;
        var raw = length.trim().toLowerCase();
        if (!raw.endsWith("px")) {
            try {
                return Double.parseDouble(raw);   // a bare number is pixels
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Double.parseDouble(raw.substring(0, raw.length() - 2).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
