package ai.mindconnect.ui.editor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for the multi-project visual editor — the server-side counterpart of
 * the browser's {@code RestProjectStore}. Sits under {@code /editor/api/projects}
 * alongside the existing schema/default endpoints ({@link EditorRestController}),
 * which server-mode reuses for the node catalogue and default instances.
 *
 * <ul>
 *   <li>{@code GET/POST /projects}, {@code PUT/DELETE /projects/{id}}</li>
 *   <li>{@code POST /projects/{id}/pages}, {@code PUT/DELETE /projects/{id}/pages/{pageId}}</li>
 *   <li>{@code GET/PUT /projects/{id}/pages/{pageId}/tree}</li>
 * </ul>
 *
 * <p>Backed by whatever {@link ProjectRepository} bean is present — in-memory by
 * default, swappable for JPA/file/S3 by the host.
 */
@RestController
@RequestMapping(path = "/editor/api/projects", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProjectsRestController {

    /** Body for create/rename — a bare name. */
    public record NameRequest(String name) {}

    private final ProjectRepository repo;

    public ProjectsRestController(ProjectRepository repo) {
        this.repo = repo;
    }

    // ── Projects ───────────────────────────────────────────────────────────

    @GetMapping
    public List<EditorProject> list() {
        return repo.listProjects();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public EditorProject create(@RequestBody(required = false) NameRequest body) {
        return repo.createProject(body != null ? body.name() : null);
    }

    @PutMapping(path = "/{projectId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EditorProject> rename(@PathVariable("projectId") String projectId,
                                                @RequestBody NameRequest body) {
        if (repo.getProject(projectId) == null) return ResponseEntity.notFound().build();
        repo.renameProject(projectId, body.name());
        return ResponseEntity.ok(repo.getProject(projectId));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable("projectId") String projectId) {
        repo.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    // ── Pages ──────────────────────────────────────────────────────────────

    @PostMapping(path = "/{projectId}/pages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EditorPage> createPage(@PathVariable("projectId") String projectId,
                                                 @RequestBody(required = false) NameRequest body) {
        var page = repo.createPage(projectId, body != null ? body.name() : null);
        return page != null ? ResponseEntity.ok(page) : ResponseEntity.notFound().build();
    }

    @PutMapping(path = "/{projectId}/pages/{pageId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> renamePage(@PathVariable("projectId") String projectId, @PathVariable("pageId") String pageId,
                                           @RequestBody NameRequest body) {
        repo.renamePage(projectId, pageId, body.name());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{projectId}/pages/{pageId}")
    public ResponseEntity<Void> deletePage(@PathVariable("projectId") String projectId, @PathVariable("pageId") String pageId) {
        repo.deletePage(projectId, pageId);
        return ResponseEntity.noContent().build();
    }

    // ── Page trees ─────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/pages/{pageId}/tree")
    public EditorContent loadTree(@PathVariable("projectId") String projectId, @PathVariable("pageId") String pageId) {
        return repo.loadTree(projectId, pageId);
    }

    @PutMapping(path = "/{projectId}/pages/{pageId}/tree", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EditorContent saveTree(@PathVariable("projectId") String projectId, @PathVariable("pageId") String pageId,
                                  @RequestBody(required = false) EditorContent body) {
        repo.saveTree(projectId, pageId, body != null ? body : EditorContent.empty());
        return repo.loadTree(projectId, pageId);
    }
}
