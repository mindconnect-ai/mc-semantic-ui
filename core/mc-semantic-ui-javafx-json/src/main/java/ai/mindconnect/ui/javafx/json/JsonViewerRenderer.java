package ai.mindconnect.ui.javafx.json;

import ai.mindconnect.ui.ext.jsonviewer.UiJsonViewer;
import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxText;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.Node;
import javafx.scene.control.TextArea;

/**
 * Paints {@code json-viewer} as pretty-printed text.
 *
 * <p>The web node is backed by a third-party component with IDE-style folding.
 * There is no desktop equivalent, and building one would be a tree widget's
 * worth of work for a node whose job is to let someone read a payload. So this
 * does the plain thing: indent it and show it.
 *
 * <p>Read-only rather than a label, because the reason a payload is on screen
 * is usually that someone wants a value out of it — a {@link TextArea} can be
 * selected and copied, and scrolls on its own when the payload is large, which
 * is the case the node exists for.
 *
 * <p>{@code expandLevel} and {@code theme} are the web component's own knobs
 * and have no counterpart here; they are ignored rather than approximated.
 */
public class JsonViewerRenderer implements FxNodeRenderer<UiJsonViewer> {

    /** Beyond this the block scrolls rather than growing without end. */
    static final int MAX_VISIBLE_ROWS = 24;

    /** Stateless and thread-safe once built, so one is enough. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Node render(UiJsonViewer node, FxRenderContext ctx) {
        var text = format(node.getJson());

        var area = new TextArea(text);
        area.getStyleClass().add("sui-json-viewer");
        area.setEditable(false);
        // JSON is read by its indentation, so it must not reflow; long lines
        // scroll sideways instead.
        area.setWrapText(false);
        area.setPrefRowCount(Math.min(MAX_VISIBLE_ROWS, Math.max(1, lines(text))));
        return area;
    }

    /**
     * Indents {@code json}, or hands it back as it came.
     *
     * <p>Malformed JSON is exactly what someone is looking at the payload to
     * find, so it is shown verbatim rather than replaced with an error.
     */
    static String format(String json) {
        if (!SuiFxText.present(json)) return "";
        try {
            return MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(MAPPER.readTree(json));
        } catch (Exception notJson) {
            return json;
        }
    }

    private static int lines(String text) {
        return (int) text.lines().count();
    }
}
