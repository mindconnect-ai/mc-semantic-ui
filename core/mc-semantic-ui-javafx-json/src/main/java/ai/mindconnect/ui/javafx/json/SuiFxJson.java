package ai.mindconnect.ui.javafx.json;

import ai.mindconnect.ui.ext.jsonviewer.UiJsonViewer;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import javafx.scene.Parent;

/**
 * Installs the JSON viewer renderer.
 *
 * <pre>{@code
 * var renderer = SuiFxRenderer.createDefaultRenderer();
 * SuiFxJson.install(renderer);
 * SuiFxJson.style(scene.getRoot());
 * }</pre>
 */
public final class SuiFxJson {

    private SuiFxJson() {
    }

    public static SuiFxRenderer install(SuiFxRenderer renderer) {
        renderer.register(UiJsonViewer.class, new JsonViewerRenderer());
        return renderer;
    }

    /** Adds this module's stylesheet, which the base {@code sui-fx.css} has no rules for. */
    public static void style(Parent root) {
        if (root == null) return;
        var css = SuiFxJson.class.getResource("/sui-fx/sui-fx-json.css");
        if (css == null) return;
        var url = css.toExternalForm();
        if (!root.getStylesheets().contains(url)) root.getStylesheets().add(url);
    }
}
