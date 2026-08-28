package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.javafx.icons.SuiFxIcon;
import ai.mindconnect.ui.model.UiNode;
import javafx.scene.Node;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything a renderer needs while painting: the bus to dispatch triggers to,
 * the enclosing form (if any), and the id index that lets
 * {@link SuiFxEventBus#applyPatch} find a painted node again.
 *
 * <p>One context is created per mount and shared by the whole tree, except for
 * the form scope — {@link #withForm} branches a child context so fields under
 * a {@code UiForm} register into that form and nothing else does.
 */
public class FxRenderContext {

    private final SuiFxRenderer renderer;
    private final FxFormScope form;
    /** id → painted node, for patches. Shared across the whole mount. */
    private final Map<String, Node> index;

    FxRenderContext(SuiFxRenderer renderer) {
        this(renderer, null, new LinkedHashMap<>());
    }

    private FxRenderContext(SuiFxRenderer renderer,
                            FxFormScope form, Map<String, Node> index) {
        this.renderer = renderer;
        this.form = form;
        this.index = index;
    }

    /** Paints a child node in this context — always use this, never the renderer directly. */
    public Node render(UiNode node) {
        return renderer.render(node, this);
    }

    /** A sibling context whose fields register into {@code scope}. See {@link FxFormScope}. */
    public FxRenderContext withForm(FxFormScope scope) {
        return new FxRenderContext(renderer, scope, index);
    }

    /**
     * The event bus driving this mount. Read lazily from the renderer, so a
     * trigger wired at paint time picks up whichever bus is attached when the
     * click actually happens.
     */
    public SuiFxEventBus bus() {
        return renderer.bus();
    }

    public SuiFxRenderer renderer() {
        return renderer;
    }

    /**
     * Paints an icon token through the renderer's resolver, or {@code null}
     * when it resolves to nothing. Renderers hang the result off their own
     * control as a graphic — see {@link SuiFxIcon#inherit}.
     */
    public SuiFxIcon icon(String name) {
        return renderer.icon(name);
    }

    /**
     * Makes a url from the model absolute against the page's base — see
     * {@link SuiFxRenderer#setDocumentBase}. Every renderer that paints a url
     * an app will later fetch, open or display should put it through here, or
     * the relative links a real server writes will not work.
     */
    public String resolve(String url) {
        return renderer.resolve(url);
    }

    /** The enclosing form, or {@code null} outside of one. */
    public FxFormScope form() {
        return form;
    }

    /** Looks up a painted node by its model id. */
    public Node byId(String id) {
        return id == null ? null : index.get(id);
    }

    /**
     * Indexes {@code node} under an id no model node owns — a slot.
     *
     * <p>{@code UiAppShell} is the case this exists for: it paints a content
     * container of its own and gives it {@link ai.mindconnect.ui.model.UiAppShell#contentId()},
     * so a patch can swap the page under a header and menu that stay put. The
     * web renderer does the same with {@code data-sui-slot="content"}.
     *
     * <p>Renderers living outside this package have no other way in, and
     * without it a slot would be invisible to {@link #byId} and so to every
     * patch.
     */
    public void indexSlot(String id, Node node) {
        index(id, node);
    }

    void index(String id, Node node) {
        if (id != null && !id.isBlank()) index.put(id, node);
    }

    Map<String, Node> index() {
        return index;
    }
}
