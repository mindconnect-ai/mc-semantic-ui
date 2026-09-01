package ai.mindconnect.ui.javafx;

import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * Installs {@code sui-fx.css} on a node of your own.
 *
 * <p>{@link SuiFxOverlay} loads the stylesheet onto itself, which is enough
 * when the overlay is the whole window. It is not enough once the app puts
 * chrome of its own around it — a toolbar, a status bar, a split pane. Those
 * nodes are outside the overlay's subtree, so they see none of its styles, and
 * anything of theirs written in terms of the {@code -sui-*} palette resolves to
 * nothing.
 *
 * <pre>{@code
 * var root = new BorderPane();
 * root.setTop(myToolbar);
 * root.setCenter(overlay);
 * SuiFxStyles.install(root);
 * }</pre>
 */
public final class SuiFxStyles {

    /** Classpath location of the JavaFX stylesheet. */
    public static final String STYLESHEET = "/sui-fx/sui-fx.css";

    private SuiFxStyles() {
    }

    /** Adds the stylesheet to {@code root}, once. */
    public static void install(Parent root) {
        if (root == null) return;
        var url = url();
        if (url != null && !root.getStylesheets().contains(url)) root.getStylesheets().add(url);
    }

    /** Adds the stylesheet to {@code scene}, once. */
    public static void install(Scene scene) {
        if (scene == null) return;
        var url = url();
        if (url != null && !scene.getStylesheets().contains(url)) scene.getStylesheets().add(url);
    }

    private static String url() {
        var css = SuiFxStyles.class.getResource(STYLESHEET);
        return css == null ? null : css.toExternalForm();
    }
}
