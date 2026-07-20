package ai.mindconnect.ui.editor;

import java.util.List;

/**
 * Storage contract for the multi-project visual editor: projects, their pages,
 * and each page's {@code UiNode} tree. The library ships
 * {@link InMemoryProjectRepository} as the default; host apps that want the
 * editor's projects to round-trip through JPA, a file, S3, … provide their own
 * {@link ProjectRepository} bean and the auto-configuration steps aside.
 *
 * <p>This is the server-side backend the browser-side {@code RestProjectStore}
 * talks to. The pure static build uses a localStorage store instead and never
 * touches this.
 */
public interface ProjectRepository {

    // ── Projects ───────────────────────────────────────────────────────────

    List<EditorProject> listProjects();

    /** The project, or {@code null} if unknown. */
    EditorProject getProject(String projectId);

    EditorProject createProject(String name);

    /** Renames a project; no-op if unknown. */
    void renameProject(String projectId, String name);

    /** Deletes a project and all its pages/trees; no-op if unknown. */
    void deleteProject(String projectId);

    // ── Pages ──────────────────────────────────────────────────────────────

    /** Creates a page in the project and returns it, or {@code null} if the project is unknown. */
    EditorPage createPage(String projectId, String name);

    void renamePage(String projectId, String pageId, String name);

    void deletePage(String projectId, String pageId);

    // ── Page trees ─────────────────────────────────────────────────────────

    /** The page's tree, or {@link EditorContent#empty()} when nothing is stored. */
    EditorContent loadTree(String projectId, String pageId);

    /** Persists the page's tree and bumps the page's {@code updatedAt}. */
    void saveTree(String projectId, String pageId, EditorContent content);
}
