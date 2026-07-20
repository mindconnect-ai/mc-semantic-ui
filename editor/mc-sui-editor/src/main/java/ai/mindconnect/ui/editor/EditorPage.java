package ai.mindconnect.ui.editor;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * One page of an {@link EditorProject} — a name plus the id under which its
 * {@code UiNode} tree is stored. Lightweight on purpose: the tree lives in the
 * repository keyed by (projectId, pageId), not inline here.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditorPage {

    private String id;
    private String name;
    private long updatedAt;
}
