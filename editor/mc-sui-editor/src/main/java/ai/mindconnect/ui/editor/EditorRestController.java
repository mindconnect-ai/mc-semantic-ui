package ai.mindconnect.ui.editor;

import ai.mindconnect.ui.model.UiNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny JSON API that the editor frontend talks to. Three endpoints, no auth,
 * no versioning — the editor is a single-tenant developer tool living
 * behind whatever the host app's security stack already enforces.
 *
 * <ul>
 *   <li>{@code GET  /editor/api/state}  — current {@link EditorContent}.</li>
 *   <li>{@code PUT  /editor/api/state}  — replace the whole content.</li>
 *   <li>{@code GET  /editor/api/schema} — node catalogue from
 *       {@link NodeRegistry}; drives the picker and property panel.</li>
 * </ul>
 *
 * <p>The path stays fixed at {@code /editor/api/...}. The user-facing path
 * ({@code /editor}) is host-configurable via
 * {@code mindconnect.sui.editor.base-path} — see {@link SuiEditorWebConfig}
 * — but the API root deliberately doesn't move so the JS bundle can hard-
 * code it. Apps that need to relocate the API can place a reverse-proxy
 * rule in front; same as the static asset path {@code /sui-editor/*}.
 */
@RestController
@RequestMapping(path = "/editor/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class EditorRestController {

    private final EditorContentStore store;
    private final NodeRegistry registry;

    public EditorRestController(EditorContentStore store, NodeRegistry registry) {
        this.store = store;
        this.registry = registry;
    }

    @GetMapping("/state")
    public EditorContent getState() {
        return store.load();
    }

    @PutMapping(path = "/state", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EditorContent putState(@RequestBody(required = false) EditorContent body) {
        store.save(body != null ? body : EditorContent.empty());
        return store.load();
    }

    @GetMapping("/schema")
    public java.util.List<NodeRegistry.NodeMeta> getSchema() {
        return registry.all();
    }

    /**
     * Returns a fresh default-populated instance of the requested node type.
     * The frontend calls this when the user picks "Add &gt; form" so the new
     * node arrives with sensible ids/labels/booleans already filled in —
     * driven by the factory lambdas registered in {@link NodeRegistry}.
     */
    @GetMapping("/default/{type}")
    public ResponseEntity<UiNode> getDefault(@PathVariable("type") String type) {
        var meta = registry.get(type);
        if (meta == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(meta.getFactory().get());
    }
}
