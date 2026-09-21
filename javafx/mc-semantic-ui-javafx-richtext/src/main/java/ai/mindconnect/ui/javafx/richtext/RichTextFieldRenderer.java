package ai.mindconnect.ui.javafx.richtext;

import ai.mindconnect.ui.html.RichTextSanitizer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.renderers.FieldRenderer;
import ai.mindconnect.ui.model.UiField;
import javafx.scene.web.HTMLEditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The field renderer with a real editor for {@code RICHTEXT}: an
 * {@link HTMLEditor}. Every other field type paints as before.
 *
 * <p>The value goes both ways through {@link RichTextSanitizer} — the
 * allowlist the browser's editor and the server use — so what the form
 * submits is the same vocabulary a RICHTEXT field has anywhere: the editor's
 * fonts and colours come back as plain formatting, never as style or script.
 */
public class RichTextFieldRenderer extends FieldRenderer {

    private static final Pattern BODY = Pattern.compile("(?is)<body[^>]*>(.*)</body>");

    @Override
    protected Bound richText(UiField node, FxRenderContext ctx) {
        var editor = new HTMLEditor();
        editor.getStyleClass().add("sui-richtext");
        String html = node.getValue() == null ? "" : String.valueOf(node.getValue());
        editor.setHtmlText(RichTextSanitizer.sanitize(html));
        editor.setPrefHeight(220);
        return new Bound(editor, () -> valueOf(editor.getHtmlText()));
    }

    /**
     * What the form submits: the editor's document reduced to its body,
     * sanitised; null when there is nothing but whitespace.
     */
    static String valueOf(String document) {
        if (document == null) return null;
        Matcher m = BODY.matcher(document);
        String body = m.find() ? m.group(1) : document;
        String clean = RichTextSanitizer.sanitize(body).strip();
        String textOnly = clean.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").strip();
        return textOnly.isEmpty() && !clean.contains("<img") ? null : clean;
    }
}
