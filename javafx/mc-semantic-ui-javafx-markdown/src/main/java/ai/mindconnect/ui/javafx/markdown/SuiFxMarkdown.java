package ai.mindconnect.ui.javafx.markdown;

import ai.mindconnect.ui.ext.markdown.UiMarkdown;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import javafx.scene.Parent;

/**
 * Installs the markdown renderer.
 *
 * <pre>{@code
 * var renderer = SuiFxRenderer.createDefaultRenderer();
 * SuiFxMarkdown.install(renderer);
 * SuiFxMarkdown.style(scene.getRoot());
 * }</pre>
 *
 * <p>A separate artifact for the same reason the type itself is: not every app
 * shows markdown, and the ones that do not should not carry a parser. It also
 * keeps {@code mc-semantic-ui-ext-markdown} free of JavaFX, which matters to
 * the servers that use it and never open a window.
 */
public final class SuiFxMarkdown {

    private SuiFxMarkdown() {
    }

    public static SuiFxRenderer install(SuiFxRenderer renderer) {
        renderer.register(UiMarkdown.class, new MarkdownRenderer());
        return renderer;
    }

    /** Adds this module's stylesheet, which the base {@code sui-fx.css} has no rules for. */
    public static void style(Parent root) {
        if (root == null) return;
        var css = SuiFxMarkdown.class.getResource("/sui-fx/sui-fx-markdown.css");
        if (css == null) return;
        var url = css.toExternalForm();
        if (!root.getStylesheets().contains(url)) root.getStylesheets().add(url);
    }
}
