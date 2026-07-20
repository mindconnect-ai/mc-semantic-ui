package ai.mindconnect.ui.editor;

/**
 * Trivial in-memory implementation of {@link EditorContentStore}. Holds the
 * current document in a single volatile field — fine for the standalone
 * showcase and any embedding that's content to lose its tree on restart.
 *
 * <p>Threadsafety: {@code volatile} is enough because the editor only ever
 * does whole-document replaces (no diff-style partial updates), so there's
 * no read-modify-write race. The single-writer assumption matches the
 * single-document scope.
 *
 * <p>Apps that need persistence override the {@link EditorContentStore}
 * bean — see {@link SuiEditorAutoConfiguration}.
 */
public class InMemoryEditorContentStore implements EditorContentStore {

    private volatile EditorContent content;

    public InMemoryEditorContentStore() {
        this.content = EditorContent.empty();
    }

    public InMemoryEditorContentStore(EditorContent initial) {
        this.content = initial != null ? initial : EditorContent.empty();
    }

    @Override
    public EditorContent load() {
        return content;
    }

    @Override
    public void save(EditorContent next) {
        this.content = next != null ? next : EditorContent.empty();
    }
}
