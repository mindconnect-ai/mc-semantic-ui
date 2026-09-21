package ai.mindconnect.ui.javafx.richtext;

import ai.mindconnect.ui.javafx.SuiFxRenderer;
import ai.mindconnect.ui.model.UiField;

/**
 * Installs a real editor for {@code RICHTEXT} fields.
 *
 * <pre>{@code
 * var renderer = SuiFxRenderer.createDefaultRenderer();
 * SuiFxRichText.install(renderer);
 * }</pre>
 *
 * <p>Without it the renderer paints a RICHTEXT field as a text area holding
 * the HTML. The editor is JavaFX's {@link javafx.scene.web.HTMLEditor}, and
 * that needs {@code javafx-web}, a WebKit build per platform — so it lives in
 * this module, and an app without rich text does not ship a browser engine.
 */
public final class SuiFxRichText {

    private SuiFxRichText() {
    }

    public static SuiFxRenderer install(SuiFxRenderer renderer) {
        renderer.register(UiField.class, new RichTextFieldRenderer());
        return renderer;
    }
}
