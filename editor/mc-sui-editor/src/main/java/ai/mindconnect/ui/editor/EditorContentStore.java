package ai.mindconnect.ui.editor;

/**
 * Storage contract for the editor's single in-memory document. The library
 * ships {@link InMemoryEditorContentStore} as the default; host apps that
 * want the editor's tree to round-trip through JPA, a file, an S3 bucket
 * etc. provide their own {@link EditorContentStore} bean and the
 * auto-configuration steps aside.
 *
 * <p>Single-document by design — see the README. Multi-document support
 * (per-{@code docId}) is a deliberate non-goal for the MVP; apps that need
 * it can implement their own {@code @RestController} on top of the model
 * classes here.
 */
public interface EditorContentStore {

    /** Returns the current content. Never {@code null}; returns an empty wrapper when nothing is stored. */
    EditorContent load();

    /** Replaces the current content. Pass {@link EditorContent#empty()} to clear. */
    void save(EditorContent content);
}
