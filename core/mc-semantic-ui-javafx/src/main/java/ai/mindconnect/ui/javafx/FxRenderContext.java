package ai.mindconnect.ui.javafx;

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

    private final SuiFxEventBus bus;
    private final SuiFxRenderer renderer;
    private final FxFormScope form;
    /** id → painted node, for patches. Shared across the whole mount. */
    private final Map<String, Node> index;

    FxRenderContext(SuiFxEventBus bus, SuiFxRenderer renderer) {
        this(bus, renderer, null, new LinkedHashMap<>());
    }

    private FxRenderContext(SuiFxEventBus bus, SuiFxRenderer renderer,
                            FxFormScope form, Map<String, Node> index) {
        this.bus = bus;
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
        return new FxRenderContext(bus, renderer, scope, index);
    }

    public SuiFxEventBus bus() {
        return bus;
    }

    public SuiFxRenderer renderer() {
        return renderer;
    }

    /** The enclosing form, or {@code null} outside of one. */
    public FxFormScope form() {
        return form;
    }

    /** Looks up a painted node by its model id. */
    public Node byId(String id) {
        return id == null ? null : index.get(id);
    }

    void index(String id, Node node) {
        if (id != null && !id.isBlank()) index.put(id, node);
    }

    Map<String, Node> index() {
        return index;
    }
}
