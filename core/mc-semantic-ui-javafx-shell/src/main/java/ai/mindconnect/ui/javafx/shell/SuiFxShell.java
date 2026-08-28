package ai.mindconnect.ui.javafx.shell;

import ai.mindconnect.ui.javafx.SuiFxRenderer;
import ai.mindconnect.ui.model.UiAppShell;
import ai.mindconnect.ui.model.UiHeader;
import ai.mindconnect.ui.model.UiIFrame;
import javafx.scene.Parent;

/**
 * Installs this module's renderers onto a {@link SuiFxRenderer}.
 *
 * <pre>{@code
 * var renderer = SuiFxRenderer.createDefaultRenderer();
 * SuiFxShell.install(renderer);
 * SuiFxShell.style(scene.getRoot());
 * }</pre>
 *
 * <p>Three node types the core renderer leaves alone, kept out of it because
 * one of them is expensive: {@code iframe} is a {@link javafx.scene.web.WebView},
 * and {@code javafx-web} carries a WebKit build per platform. An app wanting
 * an app-shell should not have to ship a browser engine it never opens.
 */
public final class SuiFxShell {

    private SuiFxShell() {
    }

    /** Registers {@code app-shell}, {@code header} and {@code iframe}. */
    public static SuiFxRenderer install(SuiFxRenderer renderer) {
        renderer.register(UiAppShell.class, new AppShellRenderer());
        renderer.register(UiHeader.class,   new HeaderRenderer());
        renderer.register(UiIFrame.class,   new IFrameRenderer());
        return renderer;
    }

    /**
     * Adds this module's stylesheet, which carries only what the core
     * {@code sui-fx.css} has no rules for — the shell's own chrome.
     */
    public static void style(Parent root) {
        if (root == null) return;
        var css = SuiFxShell.class.getResource("/sui-fx/sui-fx-shell.css");
        if (css == null) return;
        var url = css.toExternalForm();
        if (!root.getStylesheets().contains(url)) root.getStylesheets().add(url);
    }
}
