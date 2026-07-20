package ai.mindconnect.ui.editor;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * A project the visual editor works on — a named container of {@link EditorPage}
 * pages. The server-backed counterpart of the browser-side project record; the
 * multi-project shell lists these and drills into their pages.
 *
 * <p>The page trees themselves are stored separately (see
 * {@link ProjectRepository#loadTree}) so listing projects stays cheap.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditorProject {

    private String id;
    private String name;
    private long createdAt;
    private List<EditorPage> pages = new ArrayList<>();
}
