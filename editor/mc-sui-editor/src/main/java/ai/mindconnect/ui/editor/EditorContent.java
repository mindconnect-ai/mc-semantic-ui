package ai.mindconnect.ui.editor;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * The whole tree the editor currently has on screen — a single
 * {@link UiNode} of any concrete type. Pages, forms, sections, fields,
 * anything that participates in Jackson's polymorphic {@code UiNode}
 * dispatch is fair game; the editor doesn't care which.
 *
 * <p>Wrapped in a thin envelope rather than passed bare so we can later
 * decorate it with editor-only metadata (selection path, dirty flag,
 * version stamp) without touching the {@code UiNode} hierarchy itself.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditorContent {

    /**
     * The current root. {@code null} when the editor is empty (e.g. brand
     * new install, no default loaded yet); the REST layer surfaces this as
     * a 200 with {@code root: null} so clients can render an empty-state.
     */
    private UiNode root;

    public static EditorContent of(UiNode root) {
        var c = new EditorContent();
        c.root = root;
        return c;
    }

    public static EditorContent empty() {
        return new EditorContent();
    }
}
