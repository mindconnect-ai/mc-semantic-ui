package ai.mindconnect.ui.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ProjectRepository} — the default the library auto-configures.
 * Projects live in a map; page trees in a second map keyed {@code projectId/pageId}.
 * Fine for the standalone showcase and any embedding content to lose its data on
 * restart. Apps that need durability register their own {@link ProjectRepository}
 * bean (JPA / file / …) and this steps aside.
 *
 * <p>Threadsafe via concurrent maps; each operation is a whole-object replace,
 * matching the editor's whole-tree save model (no partial-update races).
 */
public class InMemoryProjectRepository implements ProjectRepository {

    private final Map<String, EditorProject> projects = new ConcurrentHashMap<>();
    private final Map<String, EditorContent> trees = new ConcurrentHashMap<>();

    private static String treeKey(String projectId, String pageId) {
        return projectId + "/" + pageId;
    }

    private static String shortId(String prefix) {
        return prefix + "-" + Long.toString(Math.abs(System.nanoTime()), 36);
    }

    @Override
    public List<EditorProject> listProjects() {
        return new ArrayList<>(projects.values());
    }

    @Override
    public EditorProject getProject(String projectId) {
        return projects.get(projectId);
    }

    @Override
    public EditorProject createProject(String name) {
        var p = new EditorProject();
        p.setId(shortId("proj"));
        p.setName(name != null && !name.isBlank() ? name : "Untitled project");
        p.setCreatedAt(System.currentTimeMillis());
        projects.put(p.getId(), p);
        return p;
    }

    @Override
    public void renameProject(String projectId, String name) {
        var p = projects.get(projectId);
        if (p != null && name != null && !name.isBlank()) p.setName(name);
    }

    @Override
    public void deleteProject(String projectId) {
        var p = projects.remove(projectId);
        if (p != null) {
            for (var page : p.getPages()) trees.remove(treeKey(projectId, page.getId()));
        }
    }

    @Override
    public EditorPage createPage(String projectId, String name) {
        var p = projects.get(projectId);
        if (p == null) return null;
        var page = new EditorPage();
        page.setId(shortId("page"));
        page.setName(name != null && !name.isBlank() ? name : "Untitled page");
        page.setUpdatedAt(System.currentTimeMillis());
        p.getPages().add(page);
        trees.put(treeKey(projectId, page.getId()), EditorContent.empty());
        return page;
    }

    @Override
    public void renamePage(String projectId, String pageId, String name) {
        var page = findPage(projectId, pageId);
        if (page != null && name != null && !name.isBlank()) page.setName(name);
    }

    @Override
    public void deletePage(String projectId, String pageId) {
        var p = projects.get(projectId);
        if (p == null) return;
        p.getPages().removeIf(pg -> pg.getId().equals(pageId));
        trees.remove(treeKey(projectId, pageId));
    }

    @Override
    public EditorContent loadTree(String projectId, String pageId) {
        return trees.getOrDefault(treeKey(projectId, pageId), EditorContent.empty());
    }

    @Override
    public void saveTree(String projectId, String pageId, EditorContent content) {
        trees.put(treeKey(projectId, pageId), content != null ? content : EditorContent.empty());
        var page = findPage(projectId, pageId);
        if (page != null) page.setUpdatedAt(System.currentTimeMillis());
    }

    private EditorPage findPage(String projectId, String pageId) {
        var p = projects.get(projectId);
        if (p == null) return null;
        return p.getPages().stream().filter(pg -> pg.getId().equals(pageId)).findFirst().orElse(null);
    }
}
