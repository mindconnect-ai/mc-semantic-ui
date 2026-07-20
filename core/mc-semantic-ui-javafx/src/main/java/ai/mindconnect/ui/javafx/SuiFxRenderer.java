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
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.Map;

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
 * var bus = new SuiFxEventBus();
 * bus.registerClientHandler("save", ctx -> repo.save(ctx.payload()));
 * Node ui = bus.mount(myUiTree);
 * stage.setScene(new Scene(new BorderPane(ui), 900, 600));
 * }</pre>
 *
 * <p>Instances are not thread-safe and are meant to be used from the JavaFX
 * application thread.
 */
public class SuiFxRenderer {

    private final Map<Class<?>, FxNodeRenderer<?>> renderers = new HashMap<>();

    /** Model class → the {@code type} discriminator, read off {@link UiNode}'s Jackson config. */
    private static final Map<Class<?>, String> TYPE_NAMES = readTypeNames();

    public SuiFxRenderer() {
        installDefaultRenderers();
    }

    /**
     * Registers the built-in renderers. The JavaFX twin of
     * {@code installDefaultHandlers()} in {@code renderer.ts}.
     */
    protected void installDefaultRenderers() {
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

    /** Registers (or replaces) the renderer for one node type. */
    public <T extends UiNode> SuiFxRenderer register(Class<T> type, FxNodeRenderer<T> renderer) {
        renderers.put(type, renderer);
        return this;
    }

    /** A fresh render context bound to {@code bus} — one per mount. */
    public FxRenderContext newContext(SuiFxEventBus bus) {
        return new FxRenderContext(bus, this);
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
