package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.javafx.renderers.ActionRenderer;
import ai.mindconnect.ui.javafx.renderers.DialogRenderer;
import ai.mindconnect.ui.javafx.renderers.DetailRenderer;
import ai.mindconnect.ui.javafx.renderers.FieldGroupRenderer;
import ai.mindconnect.ui.javafx.renderers.FieldRenderer;
import ai.mindconnect.ui.javafx.renderers.FormRenderer;
import ai.mindconnect.ui.javafx.renderers.LinkRenderer;
import ai.mindconnect.ui.javafx.renderers.ListRenderer;
import ai.mindconnect.ui.javafx.renderers.MenuButtonRenderer;
import ai.mindconnect.ui.javafx.renderers.MenuRenderer;
import ai.mindconnect.ui.javafx.renderers.ProgressRenderer;
import ai.mindconnect.ui.javafx.renderers.SectionRenderer;
import ai.mindconnect.ui.javafx.renderers.SpinnerRenderer;
import ai.mindconnect.ui.javafx.renderers.StackRenderer;
import ai.mindconnect.ui.javafx.renderers.TableRenderer;
import ai.mindconnect.ui.javafx.renderers.TextRenderer;
import ai.mindconnect.ui.javafx.renderers.TreeRenderer;
import ai.mindconnect.ui.javafx.renderers.UploadRenderer;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiDetail;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiFieldGroup;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiList;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiMenuButton;
import ai.mindconnect.ui.model.UiProgress;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiSpinner;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiTree;
import ai.mindconnect.ui.model.UiUpload;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The third renderer for the {@code UiNode} vocabulary: same model as the SSR
 * templates and the SPA renderer, painted into a JavaFX scene graph instead of
 * HTML.
 *
 * <p>Structure mirrors the SPA side deliberately — one {@link FxNodeRenderer}
 * per node type, all registered in {@link #installDefaultRenderers()}, and a
 * {@link SuiFxEventBus} that turns {@link UiTrigger}s into behaviour. Adding a
 * node type means adding a renderer here the same way you add one to
 * {@code renderer.ts}.
 *
 * <p><b>Covered types:</b> {@code form}, {@code field}, {@code fieldgroup},
 * {@code text}, {@code table}, {@code section} (tabs), {@code stack},
 * {@code action}, {@code tree}, {@code dialog}, {@code spinner},
 * {@code progress}, {@code link}, {@code menu}, {@code menu-button},
 * {@code detail}, {@code list} and {@code upload}. Anything else paints as a
 * visible placeholder rather than failing, so a tree that is only partly
 * supported still comes up.
 *
 * <p>{@code UiMenuItem} needs no renderer of its own: it extends
 * {@code UiAction}, and the menu renderers paint their own items.
 *
 * <p>Toasts are not in this list on purpose: a {@code UiToast} is not a node.
 * It arrives on a {@link ai.mindconnect.ui.model.UiPatch} and is shown by
 * {@link SuiFxOverlay}.
 *
 * <pre>{@code
 * var overlay  = new SuiFxOverlay();                    // the host surface
 * var renderer = new SuiFxRenderer().attach(overlay);   // paints into it
 * var bus      = new SuiFxEventBus(renderer);           // drives it, routes toasts
 * bus.registerClientHandler("save", ctx -> repo.save(ctx.payload()));
 * renderer.mount(myUiTree);
 * stage.setScene(new Scene(overlay, 900, 600));
 * }</pre>
 *
 * <p>Instances are not thread-safe and are meant to be used from the JavaFX
 * application thread.
 */
public class SuiFxRenderer {

    private final Map<Class<?>, FxNodeRenderer<?>> renderers = new HashMap<>();

    /** Model class → the {@code type} discriminator, read off {@link UiNode}'s Jackson config. */
    private static final Map<Class<?>, String> TYPE_NAMES = readTypeNames();

    /** The host surface painted into, once {@link #attach} has been called. */
    private SuiFxOverlay host;
    /** The event bus driving this renderer; set by the bus that owns it. */
    private SuiFxEventBus bus;
    /** The context of the current mount — holds the id → node index for patches. */
    private FxRenderContext context;

    public SuiFxRenderer() {
        installDefaultRenderers();
    }

    // ── host + bus wiring (the JavaFX twin of renderer.attach(host)) ─────────

    /**
     * Binds this renderer to the {@link SuiFxOverlay} it paints into — the
     * JavaFX equivalent of the SPA's {@code renderer.attach(hostElement)}. The
     * overlay is both the scene root and the surface toasts and the busy scrim
     * live on.
     *
     * @return this, so the call chains: {@code new SuiFxRenderer().attach(overlay)}
     */
    public SuiFxRenderer attach(SuiFxOverlay host) {
        this.host = host;
        return this;
    }

    /** The host surface, or {@code null} when the renderer is used detached (e.g. tests). */
    public SuiFxOverlay host() {
        return host;
    }

    /** The bus driving this renderer, or {@code null} before one is constructed with it. */
    public SuiFxEventBus bus() {
        return bus;
    }

    /** Called by {@link SuiFxEventBus}'s constructor so triggers can dispatch to it. */
    void setBus(SuiFxEventBus bus) {
        this.bus = bus;
    }

    // ── mount + patch (moved here from the bus, mirroring the SPA renderer) ──

    /**
     * Paints {@code root} and makes it the live tree this renderer patches
     * against. If a host was {@link #attach}ed, the painted node becomes its
     * content; either way the node is returned so a detached caller can place
     * it. Mirrors the SPA's {@code renderer.mount(node)}.
     *
     * @return the painted node
     */
    public Node mount(UiNode root) {
        this.context = newContext();
        Node node = context.render(root);
        if (host != null) host.setContent(node);
        return node;
    }

    /** The render context of the current mount — mostly useful for tests. */
    public FxRenderContext context() {
        return context;
    }

    /**
     * Applies the node operations of a {@link UiPatch} against the mounted tree:
     * each finds its target by id in the render index and repaints just that
     * subtree. Toasts are the bus's job — see {@link SuiFxEventBus#applyPatch}.
     */
    public void applyPatch(UiPatch patch) {
        if (patch == null || context == null) return;
        for (var op : patch.getPatches()) {
            try {
                apply(op);
            } catch (Exception e) {
                if (bus != null) bus.reportError(e);
            }
        }
    }

    private void apply(UiPatch.Operation op) {
        var target = context.byId(op.getTargetId());
        if (target == null) {
            if (bus != null) bus.reportError(new IllegalArgumentException(
                    "Patch target '" + op.getTargetId() + "' is not in the mounted tree"));
            return;
        }
        switch (op.getOp()) {
            case REPLACE -> replace(target, context.render(op.getNode()));
            case REMOVE -> children(target.getParent()).ifPresent(c -> c.remove(target));
            case APPEND -> children(target).ifPresent(c -> c.add(context.render(op.getNode())));
            case CLEAR -> children(target).ifPresent(ObservableList::clear);
        }
    }

    private void replace(Node target, Node replacement) {
        var siblings = children(target.getParent());
        if (siblings.isEmpty()) {
            if (bus != null) bus.reportError(new IllegalStateException(
                    "Cannot replace '" + target.getId() + "': its parent is not a Pane"));
            return;
        }
        var list = siblings.get();
        int index = list.indexOf(target);
        if (index < 0) return;
        list.set(index, replacement);
    }

    /** The mutable child list of a node, when it has one. */
    private Optional<ObservableList<Node>> children(Node node) {
        return node instanceof Pane pane ? Optional.of(pane.getChildren()) : Optional.empty();
    }

    /**
     * Registers the built-in renderers. The JavaFX twin of
     * {@code installDefaultHandlers()} in {@code renderer.ts}.
     */
    public void installDefaultRenderers() {
        register(UiText.class,       new TextRenderer());
        register(UiForm.class,       new FormRenderer());
        register(UiField.class,      new FieldRenderer());
        register(UiFieldGroup.class, new FieldGroupRenderer());
        register(UiTable.class,      new TableRenderer());
        register(UiSection.class,    new SectionRenderer());
        register(UiStack.class,      new StackRenderer());
        register(UiAction.class,     new ActionRenderer());
        register(UiTree.class,       new TreeRenderer());
        register(UiDialog.class,     new DialogRenderer());
        register(UiSpinner.class,    new SpinnerRenderer());
        register(UiProgress.class,   new ProgressRenderer());
        register(UiLink.class,       new LinkRenderer());
        register(UiMenu.class,       new MenuRenderer());
        register(UiMenuButton.class, new MenuButtonRenderer());
        register(UiDetail.class,     new DetailRenderer());
        register(UiList.class,       new ListRenderer());
        register(UiUpload.class,     new UploadRenderer());
    }

    public static SuiFxRenderer createDefaultRenderer() {
        return new SuiFxRenderer();
    }

    public static SuiFxRenderer createDefaultRenderer(SuiFxOverlay overlay) {
        return createDefaultRenderer().attach(overlay);
    }

    /** Registers (or replaces) the renderer for one node type. */
    public <T extends UiNode> SuiFxRenderer register(Class<T> type, FxNodeRenderer<T> renderer) {
        renderers.put(type, renderer);
        return this;
    }

    /** A fresh render context — one per mount. The bus is read back through this renderer. */
    public FxRenderContext newContext() {
        return new FxRenderContext(this);
    }

    /**
     * Paints {@code node} and wires the plumbing every {@link UiNode} carries:
     * the id index, style classes, and the click/hover triggers.
     *
     * <p>Prefer {@link FxRenderContext#render(UiNode)} from inside a renderer —
     * it passes the right context along.
     */
    @SuppressWarnings("unchecked")
    public Node render(UiNode node, FxRenderContext ctx) {
        if (node == null) return new Label();

        var renderer = (FxNodeRenderer<UiNode>) lookup(node.getClass());
        Node fx = renderer == null ? placeholder(node) : renderer.render(node, ctx);

        applyCommon(node, fx, ctx);
        return fx;
    }

    /** Walks up the class hierarchy so a subclass of a known type still paints. */
    private FxNodeRenderer<?> lookup(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            var r = renderers.get(c);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * The bits that belong to {@link UiNode} itself rather than to any one
     * type: id indexing (so patches can find the node again), style classes,
     * and the shared triggers.
     */
    private void applyCommon(UiNode node, Node fx, FxRenderContext ctx) {
        fx.getStyleClass().add("sui-" + typeName(node.getClass()));
        if (node.getCssClass() != null) {
            for (String token : node.getCssClass().trim().split("\\s+")) {
                if (!token.isEmpty()) fx.getStyleClass().add(token);
            }
        }
        if (node.getId() != null && !node.getId().isBlank()) {
            fx.setId(node.getId());
            ctx.index(node.getId(), fx);
        }
        // Keep the model on the painted node so a patch can re-render in place.
        fx.getProperties().put(SuiFxEventBus.MODEL_KEY, node);

        wireTriggers(node, fx, ctx);
    }

    private void wireTriggers(UiNode node, Node fx, FxRenderContext ctx) {
        // A renderer that owns its own click (a button's onAction, a tab's
        // selection) marks the node; wiring a second handler here would fire
        // the trigger twice.
        boolean clickHandled = Boolean.TRUE.equals(fx.getProperties().get(SuiFxEventBus.CLICK_HANDLED_KEY));

        UiTrigger click = node.getOnClick();
        UiTrigger dblClick = node.getOnDblClick();
        if (!clickHandled && (click != null || dblClick != null)) {
            fx.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                UiTrigger t = e.getClickCount() >= 2 && dblClick != null ? dblClick : click;
                if (t == null) return;
                // The nearest handler wins: stop the click from also firing a
                // clickable ancestor, matching the SPA's delegation rules.
                e.consume();
                ctx.bus().dispatch(t, node, ctx);
            });
        }
        if (node.getOnHover() != null) {
            fx.addEventHandler(MouseEvent.MOUSE_ENTERED,
                    e -> ctx.bus().dispatch(node.getOnHover(), node, ctx));
        }
        if (node.getOnLeave() != null) {
            fx.addEventHandler(MouseEvent.MOUSE_EXITED,
                    e -> ctx.bus().dispatch(node.getOnLeave(), node, ctx));
        }
        // onChange / onInput need the concrete control and are wired by
        // FieldRenderer, which owns it.
    }

    private Node placeholder(UiNode node) {
        var label = new Label("[" + typeName(node.getClass()) + " not yet supported by the JavaFX renderer]");
        label.getStyleClass().add("sui-unsupported");
        return label;
    }

    /**
     * The {@code type} discriminator for a model class — the same string the
     * SSR template name and the TS union use.
     */
    public static String typeName(Class<?> type) {
        var name = TYPE_NAMES.get(type);
        if (name != null) return name;
        // Extension types register with Jackson at runtime rather than in
        // UiNode's @JsonSubTypes; derive a stable fallback from the class name.
        var simple = type.getSimpleName();
        if (simple.startsWith("Ui")) simple = simple.substring(2);
        return simple.toLowerCase();
    }

    private static Map<Class<?>, String> readTypeNames() {
        var map = new HashMap<Class<?>, String>();
        var subTypes = UiNode.class.getAnnotation(JsonSubTypes.class);
        if (subTypes != null) {
            for (var t : subTypes.value()) map.put(t.value(), t.name());
        }
        return map;
    }
}
