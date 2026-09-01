package ai.mindconnect.ui.javafx.iframe;

import ai.mindconnect.ui.javafx.SuiFxRenderer;
import ai.mindconnect.ui.model.UiIFrame;
import javafx.scene.Parent;

/**
 * Installs the {@code iframe} renderer.
 *
 * <pre>{@code
 * var renderer = SuiFxRenderer.createDefaultRenderer();
 * SuiFxIFrame.install(renderer);
 * SuiFxIFrame.style(scene.getRoot());
 * }</pre>
 *
 * <p>One node type, one module. {@code iframe} is a {@link javafx.scene.web.WebView},
 * and {@code javafx-web} carries a WebKit build for every platform it supports —
 * an app that embeds no pages should not have to ship a browser engine to draw
 * the rest of the vocabulary. Everything else the desktop paints, including the
 * app shell and its header, is in {@code mc-semantic-ui-javafx} and needs no
 * installing.
 */
public final class SuiFxIFrame {

    private SuiFxIFrame() {
    }

    public static SuiFxRenderer install(SuiFxRenderer renderer) {
        renderer.register(UiIFrame.class, new IFrameRenderer());
        return renderer;
    }

    /** Adds this module's stylesheet, which the base {@code sui-fx.css} has no rules for. */
    public static void style(Parent root) {
        if (root == null) return;
        var css = SuiFxIFrame.class.getResource("/sui-fx/sui-fx-iframe.css");
        if (css == null) return;
        var url = css.toExternalForm();
        if (!root.getStylesheets().contains(url)) root.getStylesheets().add(url);
    }
}
