package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiRow;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.model.UiUpload;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Turns a {@link UiNode} tree into a live JavaFX application — the rich-client
 * counterpart of the SPA's {@code SuiEventBus}.
 *
 * <p>It owns the same three things the web bus owns:
 * <ul>
 *   <li><b>Behaviours</b> — one per {@link UiTrigger.Behavior}; what actually
 *       happens when a trigger fires. Override any of them by name.</li>
 *   <li><b>Client handlers</b> — plain Java methods registered under a name and
 *       called by {@code INVOKE}. In a rich client this is the <em>main</em>
 *       path: no HTTP, just local code. See {@link FxClientHandler}.</li>
 *   <li><b>Patching</b> — {@link UiPatch} operations applied against the
 *       painted scene graph, so a handler can replace a table or append a row
 *       without repainting the window.</li>
 * </ul>
 *
 * <p>The HTTP behaviours ({@code APPLY_RESPONSE}, {@code DOWNLOAD},
 * {@code OPEN_IN_TAB}, {@code UPLOAD}) are provided so a JavaFX front end can
 * talk to the same endpoints the web client does. {@code STREAM} is not
 * implemented yet and reports through {@link #setOnError}.
 *
 * <p>Note that a rich client usually does not need {@code UPLOAD} at all:
 * picking a file and reading it is local work, so the natural model is
 * {@code UiUpload.onUpload(UiTrigger.invoke("import"))} — the handler then
 * reads {@link FxTriggerContext#files()} straight off the disk, no server
 * involved. {@code UPLOAD} exists for the case where the model names a url,
 * so the same tree the browser posts also posts here.
 *
 * <p>All scene-graph work happens on the JavaFX application thread; network
 * calls run on a small background pool and hop back via
 * {@link Platform#runLater}.
 */
public class SuiFxEventBus {

    /** Scene-graph property key under which each painted node keeps its model. */
    public static final Object MODEL_KEY = "sui.model";
    /**
     * Marks a node whose renderer already wired the click itself (a button, a
     * tab). {@link SuiFxRenderer} then skips its generic click wiring so the
     * trigger does not fire twice.
     */
    public static final Object CLICK_HANDLED_KEY = "sui.clickHandled";
    /** Style class put on the clicked control while its trigger is in flight. */
    public static final String LOADING_CLASS = "is-loading";

    /**
     * The id the server appends dialogs to. The SPA creates a real container
     * under it; here it names no node at all, and is recognised purely so the
     * operations aimed at it can be turned into windows.
     */
    public static final String DIALOG_HOST_ID = "sui-dialogs";

    /** How much of the screen a dialog may take before its content scrolls. */
    static final double DIALOG_MAX_SCREEN_FRACTION = 0.8;

    private final SuiFxRenderer renderer;
    private final ObjectMapper mapper;
    private final Map<String, FxBehaviorHandler> behaviors = new ConcurrentHashMap<>();
    private final Map<String, Registration> clientHandlers = new ConcurrentHashMap<>();
    /** Node id → its current values, for {@link UiTrigger#getPayload()} resolution. */
    private final Map<String, Supplier<Map<String, Object>>> payloadSources = new ConcurrentHashMap<>();

    private final HttpClient http = HttpClient.newHttpClient();
    private final ExecutorService io = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "sui-fx-io");
        t.setDaemon(true);
        return t;
    });

    private FxLoadingPolicy loadingPolicy = FxLoadingPolicy.AUTO;
    private Consumer<Boolean> busyIndicator = busy -> { };
    private Consumer<UiToast> toastHandler = SuiFxEventBus::defaultToast;
    private Consumer<Throwable> errorHandler = err ->
            System.getLogger(SuiFxEventBus.class.getName())
                    .log(System.Logger.Level.ERROR, "SuiFxEventBus: trigger failed", err);
    private Consumer<String> linkOpener = SuiFxEventBus::browse;
    /** No address bar on a desktop window, so a navigate hint goes nowhere by default. */
    private Consumer<String> navigateHandler = href -> { };
    /** A page's resume list reconnects by default, now that this bus reads SSE. */
    private Consumer<List<UiPage.ActiveStream>> activeStreamHandler = this::reconnectMissingStreams;
    /** Open dialog windows by node id, so a patch can close the one it names. */
    private final java.util.Map<String, Stage> openDialogs = new java.util.LinkedHashMap<>();
    /** Live SSE streams by channel id. A stream outlives the tree it started from. */
    private final Map<String, FxStreamHandle> streams = new ConcurrentHashMap<>();
    private final Map<String, FxStreamEventHandler> streamEventHandlers = new ConcurrentHashMap<>();
    /**
     * What a finished {@code DOWNLOAD} does with the bytes. The default asks
     * where to put them — logging the path of a temp file, which is what this
     * used to do, is indistinguishable from the download not happening.
     */
    private Consumer<File> downloadHandler = this::saveAs;

    /**
     * The primary constructor: a bus driving {@code renderer}. If the renderer
     * was {@link SuiFxRenderer#attach}ed to a {@link SuiFxOverlay}, toasts and
     * the busy scrim are wired to it automatically.
     *
     * <pre>{@code
     * var overlay  = new SuiFxOverlay();
     * var renderer = new SuiFxRenderer().attach(overlay);
     * var bus      = new SuiFxEventBus(renderer);
     * renderer.mount(tree);
     * }</pre>
     */
    public SuiFxEventBus(SuiFxRenderer renderer) {
        this(renderer, defaultMapper());
    }

    /**
     * The mapper a bus uses when it is given none.
     *
     * <p>Two things a plain {@code new ObjectMapper()} gets wrong here.
     *
     * <p>It finds no extension node types. {@code markdown}, {@code chart},
     * {@code diagram} and {@code json-viewer} register themselves through
     * Jackson's ServiceLoader, which is what {@code findAndRegisterModules()}
     * reads — without it a page containing any of them fails to parse at all.
     *
     * <p>And it treats a type it has never heard of as fatal, losing the whole
     * page over one node. The SPA renderer does not: an unknown node paints a
     * placeholder and the rest of the screen comes up. Disabling
     * {@code FAIL_ON_INVALID_SUBTYPE} gets the same outcome here — the unknown
     * node arrives as {@code null} and paints as nothing, while everything
     * around it survives.
     */
    public static ObjectMapper defaultMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
    }

    public SuiFxEventBus(SuiFxRenderer renderer, ObjectMapper mapper) {
        this.renderer = renderer;
        this.mapper = mapper;
        renderer.setBus(this);
        installDefaultBehaviors();
        // A patch event is universal enough to be built in; an app can replace
        // it by registering another handler under the same name.
        onStreamEvent("patch", (data, handle) -> {
            try {
                applyPatch(mapper.readValue(data, UiPatch.class));
            } catch (Exception e) {
                reportError(e);
            }
        });
        // The overlay is both the host and the toast/busy surface, so if the
        // renderer is already attached to one, wire it up with no extra call.
        if (renderer.host() != null) setOverlay(renderer.host());
    }

    /** @deprecated Prefer {@code new SuiFxEventBus(new SuiFxRenderer().attach(overlay))}. */
    @Deprecated
    public SuiFxEventBus() {
        this(new SuiFxRenderer());
    }

    // ── mounting ──────────────────────────────────────────────────────────

    /**
     * @deprecated Mounting moved to the renderer, mirroring the SPA's
     *     {@code renderer.mount(node)}. Call {@link SuiFxRenderer#mount} instead.
     */
    @Deprecated
    public Node mount(UiNode root) {
        return renderer.mount(root);
    }

    /** The render context of the current mount — mostly useful for tests. */
    public FxRenderContext context() {
        return renderer.context();
    }

    public SuiFxRenderer renderer() {
        return renderer;
    }

    // ── registration ──────────────────────────────────────────────────────

    /** Registers (or replaces) a behaviour. The name is a {@link UiTrigger.Behavior} constant. */
    public SuiFxEventBus registerBehavior(String name, FxBehaviorHandler handler) {
        behaviors.put(name, handler);
        return this;
    }

    /**
     * Registers a local handler for {@code INVOKE} triggers — a browser-less
     * "endpoint" backed by an ordinary Java method. See {@link FxClientHandler}.
     *
     * <p>The handler runs on a background thread, so it may do real work
     * without freezing the window. See
     * {@link #registerClientHandler(String, FxClientHandler, FxHandlerThread)}
     * to opt out.
     */
    public SuiFxEventBus registerClientHandler(String name, FxClientHandler handler) {
        return registerClientHandler(name, handler, FxHandlerThread.BACKGROUND);
    }

    /** As above, choosing which thread the handler runs on. See {@link FxHandlerThread}. */
    public SuiFxEventBus registerClientHandler(String name, FxClientHandler handler,
                                               FxHandlerThread thread) {
        clientHandlers.put(name, new Registration(handler, thread));
        return this;
    }

    private record Registration(FxClientHandler handler, FxHandlerThread thread) { }

    /**
     * Registers a payload source under a node id, so a trigger built with
     * {@code UiTrigger.invoke("save", "customer-form")} finds the values.
     * Forms register themselves; call this for your own collectible nodes.
     */
    public SuiFxEventBus registerPayloadSource(String nodeId, Supplier<Map<String, Object>> values) {
        if (nodeId != null && !nodeId.isBlank()) payloadSources.put(nodeId, values);
        return this;
    }

    /** Where {@link UiToast}s go. Default shows a modal alert; an overlay is nicer. */
    public SuiFxEventBus setToastHandler(Consumer<UiToast> handler) {
        this.toastHandler = handler;
        return this;
    }

    /**
     * Routes toasts and the global busy state into an overlay layer — proper
     * toast cards instead of modal alerts, and a busy scrim instead of nothing.
     *
     * <p>Usually you need not call this: constructing the bus with a renderer
     * that was {@link SuiFxRenderer#attach}ed to an overlay wires it for you.
     * Use it only to attach or swap an overlay after the fact.
     */
    public SuiFxEventBus setOverlay(SuiFxOverlay overlay) {
        if (overlay == null) return this;
        setToastHandler(overlay::toast);
        setBusyIndicator(overlay::setBusy);
        return this;
    }

    /** Called when a behaviour or handler throws. Default logs. */
    public SuiFxEventBus setOnError(Consumer<Throwable> handler) {
        this.errorHandler = handler;
        return this;
    }

    /** How {@code OPEN_IN_TAB} opens a URL. Default hands it to the desktop browser. */
    public SuiFxEventBus setLinkOpener(Consumer<String> opener) {
        this.linkOpener = opener;
        return this;
    }

    /** Called with the temp file a {@code DOWNLOAD} produced. */
    public SuiFxEventBus setDownloadHandler(Consumer<File> handler) {
        this.downloadHandler = handler;
        return this;
    }

    // ── dispatch ──────────────────────────────────────────────────────────

    /**
     * Fires a trigger. Resolves its payload, looks up the behaviour and runs
     * it, routing anything thrown to {@link #setOnError}.
     */
    public void dispatch(UiTrigger trigger, UiNode source, FxRenderContext ctx) {
        dispatch(trigger, source, ctx, List.of());
    }

    /** As {@link #dispatch(UiTrigger, UiNode, FxRenderContext)}, with picked files attached. */
    public void dispatch(UiTrigger trigger, UiNode source, FxRenderContext ctx, List<File> files) {
        if (trigger == null) return;
        // Handlers run off-thread, so a follow-on dispatch from inside one
        // arrives here on a background thread. Marking the clicked control
        // busy touches the scene graph, so get onto the FX thread first.
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> dispatch(trigger, source, ctx, files));
            return;
        }

        var name = trigger.getBehavior() == null
                ? UiTrigger.Behavior.APPLY_RESPONSE.name()
                : trigger.getBehavior().name();
        var handler = behaviors.get(name);
        if (handler == null) {
            reportError(new IllegalStateException("No behaviour registered for '" + name + "'"));
            return;
        }

        var payload = resolvePayload(trigger, source, ctx);
        var triggerCtx = new FxTriggerContext(this, trigger, source,
                ctx == null ? renderer.context() : ctx, payload, files);

        // Busy for exactly as long as the behaviour runs: until handle()
        // returns for synchronous work, until its stage completes for async.
        // Same lifecycle the SPA ties its is-loading class to.
        boolean busy = loadingPolicy.showLoading(triggerCtx);
        var busySource = busy ? markBusy(source, triggerCtx.render()) : null;
        if (busy) setBusy(true);

        try {
            var stage = handler.handle(triggerCtx);
            if (stage == null) {
                if (busy) clearBusy(busySource);
            } else {
                stage.whenComplete((ok, err) -> onFxThread(() -> {
                    if (busy) clearBusy(busySource);
                    if (err != null) reportError(err);
                }));
            }
        } catch (Exception e) {
            if (busy) clearBusy(busySource);
            reportError(e);
        }
    }

    // ── busy state ────────────────────────────────────────────────────────

    /** Picks whether dispatches show a loading indicator. See {@link FxLoadingPolicy}. */
    public SuiFxEventBus setLoadingPolicy(FxLoadingPolicy policy) {
        this.loadingPolicy = policy == null ? FxLoadingPolicy.AUTO : policy;
        return this;
    }

    /**
     * Raises or lowers the global busy state. Wired to the overlay by
     * {@link #setOverlay}; call it yourself under a {@code MANUAL} policy.
     */
    public void setBusy(boolean busy) {
        busyIndicator.accept(busy);
    }

    /** Replaces what the global busy state does. Default: nothing until an overlay is set. */
    public SuiFxEventBus setBusyIndicator(Consumer<Boolean> indicator) {
        this.busyIndicator = indicator == null ? b -> { } : indicator;
        return this;
    }

    /**
     * Runs slow work off the JavaFX thread while keeping the busy state up.
     *
     * <p>An {@link FxClientHandler} is a plain Java method and therefore
     * synchronous: the busy state ends when it returns. That is right for the
     * usual local handler, which finishes in microseconds — but a handler that
     * hits a database or a slow service must not block the FX thread. Hand
     * that work to this method and the indicator stays up until it is done:
     *
     * <pre>{@code
     * bus.registerClientHandler("sync", ctx ->
     *         bus.runAsync(() -> inventory.syncWithSupplier())
     *            .thenRun(() -> bus.toast(UiToast.success("Synced"))));
     * }</pre>
     *
     * <p>The returned stage completes on the JavaFX thread, so its
     * continuations may touch the scene graph. Failures go to
     * {@link #setOnError}.
     */
    public CompletableFuture<Void> runAsync(Runnable work) {
        setBusy(true);
        var done = submit(work::run);
        done.whenComplete((ok, err) -> {
            setBusy(false);
            // Nobody else is watching this one — a dispatch reports its own
            // behaviour's failures, but a direct runAsync has no such owner.
            if (err != null) reportError(err);
        });
        return done;
    }

    /**
     * Runs {@code work} on the background pool and completes on the JavaFX
     * thread. Busy accounting is the caller's business — a dispatch already
     * does it around the whole behaviour, so this must not double-count.
     */
    private CompletableFuture<Void> submit(ThrowingRunnable work) {
        var done = new CompletableFuture<Void>();
        io.execute(() -> {
            Throwable failure = null;
            try {
                work.run();
            } catch (Throwable t) {
                failure = t;
            }
            var error = failure;
            // Complete on the FX thread so continuations may touch the scene
            // graph without thinking about it.
            onFxThread(() -> {
                if (error != null) done.completeExceptionally(error);
                else done.complete(null);
            });
        });
        return done;
    }

    /** A {@link Runnable} that may throw — what a client handler is. */
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Marks the control the user actually clicked as busy — it gets the
     * {@code is-loading} style class and stops responding, so a double-click
     * cannot fire the same trigger twice while it is in flight.
     *
     * @return the marked control, or {@code null} when the source has no
     *         painted counterpart (a table row's action, say)
     */
    private Node markBusy(UiNode source, FxRenderContext ctx) {
        if (source == null || ctx == null) return null;
        var control = ctx.byId(source.getId());
        if (control == null) return null;
        control.getStyleClass().add(LOADING_CLASS);
        control.setDisable(true);
        return control;
    }

    private void clearBusy(Node control) {
        setBusy(false);
        if (control == null) return;
        control.getStyleClass().remove(LOADING_CLASS);
        control.setDisable(false);
    }

    /**
     * Collects the values of the node named by {@link UiTrigger#getPayload()}.
     *
     * <p>Without an explicit name, the nearest payload wins — the row for a
     * row action, otherwise the enclosing form. That is what lets
     * {@code UiTrigger.invoke("deleteOrder")} on a row action arrive with the
     * row's data, and a submit button inside a form skip naming the form.
     */
    private Map<String, Object> resolvePayload(UiTrigger trigger, UiNode source, FxRenderContext ctx) {
        var payloadId = trigger.getPayload();
        if (payloadId != null) {
            var values = payloadSources.get(payloadId);
            return values == null ? Map.of() : values.get();
        }
        if (source instanceof UiRow row) {
            return row.getData();
        }
        if (ctx != null && ctx.form() != null) {
            return ctx.form().values();
        }
        return Map.of();
    }

    // ── patching ──────────────────────────────────────────────────────────

    /**
     * Applies a {@link UiPatch} against the mounted tree: each operation finds
     * its target by id in the render index and repaints just that subtree.
     * Toasts in the patch go to the toast handler.
     */
    public void applyPatch(UiPatch patch) {
        if (patch == null) return;
        onFxThread(() -> {
            // In order, one at a time. Dialogs are windows here rather than
            // scene-graph children, so those operations are intercepted — but
            // collecting the rest and running them afterwards reorders the
            // patch, and a patch means what it means in sequence.
            //
            // The case that proves it is the ordinary "swap this dialog":
            // REMOVE wf-dialog, then APPEND a new wf-dialog. Deferred, the
            // remove ran after the append had already re-indexed that id, so
            // it deleted the new dialog's content and the window came up
            // empty.
            for (var op : patch.getPatches()) {
                if (!applyDialogOperation(op)) {
                    // Node operations are the renderer's job — it owns the id
                    // index — and it takes them a patch at a time.
                    renderer.applyPatch(UiPatch.of().patch(op));
                }
            }
            // Toasts are the bus's, since only it knows the toast handler.
            if (patch.getToasts() != null) patch.getToasts().forEach(toastHandler);
        });
    }

    /**
     * Handles the operations that address dialogs rather than the tree.
     *
     * <p>The SPA keeps a body-level {@code #sui-dialogs} host and opens a
     * dialog by appending it there, closes it by removing it by id. A desktop
     * has no such container — a dialog is a window — so those operations are
     * intercepted here and turned into windows. Without this the server's
     * "open a dialog" patch finds no target and the button appears to do
     * nothing at all, which is what it did.
     *
     * @return {@code true} when the operation was handled and must not go on
     *         to the renderer
     */
    private boolean applyDialogOperation(UiPatch.Operation op) {
        if (op == null || op.getOp() == null) return false;
        var target = op.getTargetId();

        if (DIALOG_HOST_ID.equals(target)) {
            switch (op.getOp()) {
                case APPEND -> openDialog(op.getNode());
                case REPLACE -> {
                    closeAllDialogs();
                    openDialog(op.getNode());
                }
                case CLEAR -> closeAllDialogs();
                default -> { }
            }
            return true;
        }

        var stage = openDialogs.get(target);
        if (stage != null) {
            switch (op.getOp()) {
                case REMOVE -> closeDialog(target);
                case REPLACE -> {
                    closeDialog(target);
                    openDialog(op.getNode());
                }
                // Anything else names a node inside the dialog's own tree,
                // which the renderer indexed when it painted it.
                default -> {
                    return false;
                }
            }
            return true;
        }

        // "Make sure this dialog is gone" for one that is not open. The SPA
        // removes nothing and carries on; the renderer would report a missing
        // target, so swallow it here.
        return op.getOp() == UiPatch.Op.REMOVE
                && (renderer.context() == null || renderer.context().byId(target) == null);
    }

    /**
     * Applies a {@link UiPage} — the response envelope a full navigation comes
     * back in, as opposed to the in-place {@link UiPatch}.
     *
     * <p>This is what makes {@code UiTrigger.go(href)} work on the desktop: it
     * is an {@code APPLY_RESPONSE} GET, and the page it fetches arrives here.
     *
     * <p>A page is a fresh screen, so it remounts the tree and drops the
     * previous page's dialogs before opening its own — the same reset the SPA
     * performs on its dialog host.
     *
     * <p>Two fields have no desktop counterpart and are handed to a handler
     * rather than acted on: {@code navigate} is an address-bar push, and a
     * window has no address bar; {@code activeStreams} asks the client to
     * re-attach to server-sent event streams, which this bus does not read
     * (its {@code STREAM} behaviour throws). Both default to doing nothing.
     * See {@link #setNavigateHandler} and {@link #setActiveStreamHandler}.
     */
    public void applyPage(UiPage page) {
        applyPage(page, null);
    }

    /**
     * Applies a page fetched from {@code sourceUrl}, which becomes the base
     * every relative url in it resolves against — see
     * {@link SuiFxRenderer#setDocumentBase}. A page's own {@code navigate}
     * refines it, the way a redirect does in a browser.
     */
    public void applyPage(UiPage page, String sourceUrl) {
        if (page == null) return;
        onFxThread(() -> {
            // Before the mount: renderers paint asset urls (a brand logo, an
            // iframe src) while the tree is being built, and they resolve
            // against whatever the base is at that moment.
            if (sourceUrl != null) renderer.setDocumentBase(renderer.resolve(sourceUrl));
            if (page.getNavigate() != null) {
                renderer.setDocumentBase(renderer.resolve(page.getNavigate()));
            }
            if (page.getNode() != null) renderer.mount(page.getNode());

            // The tree the old dialogs were anchored to is gone; close them
            // before painting this page's own.
            closeAllDialogs();
            if (page.getDialogs() != null) page.getDialogs().forEach(this::openDialog);

            if (page.getToasts() != null) page.getToasts().forEach(toastHandler);
            if (page.getNavigate() != null) {
                navigateHandler.accept(renderer.resolve(page.getNavigate()));
            }
            if (page.getActiveStreams() != null && !page.getActiveStreams().isEmpty()) {
                activeStreamHandler.accept(page.getActiveStreams());
            }
        });
    }

    /** Opens a dialog node as a window and remembers it under its id. */
    private void openDialog(UiNode node) {
        if (!(node instanceof UiDialog dialog)) return;
        var stage = showDialog(dialog);
        if (stage == null) return;

        var id = dialog.getId() == null ? "sui-dialog-" + UUID.randomUUID() : dialog.getId();
        openDialogs.put(id, stage);
        // The user can close the window themselves; drop it either way, or a
        // later patch would try to close a stage that is already gone.
        stage.setOnHidden(e -> openDialogs.remove(id, stage));
    }

    private void closeDialog(String id) {
        var stage = openDialogs.remove(id);
        if (stage != null) stage.close();
    }

    /** Closes every dialog this bus has open. */
    private void closeAllDialogs() {
        List.copyOf(openDialogs.values()).forEach(Stage::close);
        openDialogs.clear();
    }

    /**
     * What to do with a page's {@code navigate} hint. On the web it is a
     * history push; a desktop window has no address bar, so the default does
     * nothing. Set it if the app keeps its own history, breadcrumb or
     * back button.
     */
    public SuiFxEventBus setNavigateHandler(Consumer<String> handler) {
        this.navigateHandler = handler == null ? href -> { } : handler;
        return this;
    }

    /**
     * What to do with the streams a page says are still running. This bus has
     * no SSE reader — its {@code STREAM} behaviour throws — so the default does
     * nothing. An app that registered its own {@code STREAM} behaviour can hook
     * this to reconnect the channels it is not already reading.
     */
    public SuiFxEventBus setActiveStreamHandler(Consumer<List<UiPage.ActiveStream>> handler) {
        this.activeStreamHandler = handler == null ? streams -> { } : handler;
        return this;
    }

    /** Shows a toast through the configured handler. */
    public void toast(UiToast toast) {
        if (toast != null) onFxThread(() -> toastHandler.accept(toast));
    }

    /**
     * Opens a {@link UiDialog} as a modal window, painted by the normal
     * {@code dialog} renderer plus a close button.
     *
     * <p>{@link UiDialog#getCloseHref()} is what closing means: the window
     * goes away and the href is dispatched, so the server (or a local handler)
     * learns the dialog was dismissed. Without one, closing is purely local.
     *
     * @return the stage, already shown — keep it if you want to close it yourself
     */
    public Stage showDialog(UiDialog dialog) {
        // Opening a dialog straight out of a background client handler is the
        // obvious thing to write, so make it work: hop to the FX thread and
        // wait for the stage rather than making every caller wrap the call.
        if (!Platform.isFxApplicationThread()) {
            var task = new FutureTask<>(() -> showDialog(dialog));
            Platform.runLater(task);
            try {
                return task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                reportError(e.getCause());
                return null;
            }
        }

        // Rendered in the mounted page's context on purpose: that is what puts
        // the dialog's fields in the id index, so a patch can reach into an
        // open dialog the way it reaches into the page.
        if (renderer.context() == null) {
            reportError(new IllegalStateException(
                    "Cannot show a dialog before a page is mounted"));
            return null;
        }
        var content = renderer.context().render(dialog);

        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (dialog.getTitle() != null) stage.setTitle(dialog.getTitle());

        var close = new Button("Close");
        close.setOnAction(e -> stage.close());
        var footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(0, 16, 16, 16));

        // A window sizes itself to its content, and content has no idea how big
        // the screen is: a long form grew the dialog straight off the bottom,
        // with the Close button somewhere below the taskbar. It scrolls
        // instead, and stops at a size that still fits where it is shown.
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("sui-dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        var root = new VBox(scroll, footer);
        root.getStyleClass().add("sui-dialog");

        // Visual bounds, not the raw resolution: it excludes the menu bar and
        // the taskbar, which are exactly what a maximum-height window would
        // otherwise hide itself behind.
        var bounds = Screen.getPrimary().getVisualBounds();
        root.setMaxHeight(bounds.getHeight() * DIALOG_MAX_SCREEN_FRACTION);
        root.setMaxWidth(bounds.getWidth() * DIALOG_MAX_SCREEN_FRACTION);

        // Whichever way it closes — the button, the window's own close box —
        // the model's closeHref fires exactly once.
        stage.setOnHidden(e -> {
            if (dialog.getCloseHref() != null) {
                dispatch(UiTrigger.go(dialog.getCloseHref()), dialog, renderer.context());
            }
        });

        stage.setScene(new Scene(root));
        inheritStylesheets(stage);
        stage.show();
        return stage;
    }

    /**
     * Gives the dialog the look of the window it came from.
     *
     * <p>A dialog is its own scene, and a scene starts from nothing but the
     * JavaFX default theme — which is why an unstyled one comes up looking
     * like a different application altogether.
     *
     * <p>Copying the owner scene's stylesheets is not enough on its own.
     * {@link SuiFxOverlay} loads {@code sui-fx.css} onto <em>itself</em>, and
     * {@link SuiFxStyles#install(Parent)} exists precisely so an app can do the
     * same on a root of its own — in both cases the scene's own list is empty
     * and there is nothing to inherit. So the palette is installed outright,
     * and the owner is then asked for both lists: what it put on its scene, and
     * what it put on its root.
     */
    private void inheritStylesheets(Stage dialogStage) {
        var scene = dialogStage.getScene();
        SuiFxStyles.install(scene);

        // The window the user is actually working in: a second window of the
        // same app must not decide how this dialog looks.
        Window owner = frontWindow(dialogStage);
        if (owner instanceof Stage ownerStage && ownerStage.getScene() != null) {
            dialogStage.initOwner(ownerStage);
            var from = ownerStage.getScene();
            adopt(scene, from.getStylesheets());
            if (from.getRoot() != null) adopt(scene, from.getRoot().getStylesheets());
        }
    }

    private static void adopt(Scene scene, List<String> stylesheets) {
        for (var sheet : stylesheets) {
            if (!scene.getStylesheets().contains(sheet)) scene.getStylesheets().add(sheet);
        }
    }

    public void reportError(Throwable error) {
        try {
            errorHandler.accept(error);
        } catch (Exception e) {
            System.getLogger(SuiFxEventBus.class.getName())
                    .log(System.Logger.Level.ERROR, "SuiFxEventBus: error handler itself failed", e);
        }
    }

    // ── built-in behaviours ───────────────────────────────────────────────

    private void installDefaultBehaviors() {
        registerBehavior(UiTrigger.Behavior.INVOKE.name(), ctx -> {
            var registration = clientHandlers.get(ctx.trigger().getHandler());
            if (registration == null) {
                throw new IllegalStateException(
                        "No client handler registered for '" + ctx.trigger().getHandler() + "'");
            }
            if (registration.thread() == FxHandlerThread.FX) {
                registration.handler().handle(ctx);
                return null;
            }
            // Off-thread by default, and the returned stage is what keeps the
            // button spinning until the work is actually finished.
            return submit(() -> registration.handler().handle(ctx));
        });

        registerBehavior(UiTrigger.Behavior.PATCH.name(), ctx -> {
            applyPatch(ctx.trigger().getPatch());
            return null;
        });

        registerBehavior(UiTrigger.Behavior.APPLY_RESPONSE.name(), ctx ->
                sendAsync(ctx, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(body -> onFxThread(
                                () -> applyResponse(body, ctx.trigger().getUrl()))));

        registerBehavior(UiTrigger.Behavior.OPEN_IN_TAB.name(), ctx -> {
            linkOpener.accept(renderer.resolve(ctx.trigger().getUrl()));
            return null;
        });

        registerBehavior(UiTrigger.Behavior.DOWNLOAD.name(), ctx ->
                exchange(ctx, HttpResponse.BodyHandlers.ofByteArray())
                        .thenAccept(response -> {
                            try {
                                // A temp *file* cannot keep the name the server
                                // gave, and the name is most of what makes a
                                // download useful — so it gets a folder of its
                                // own and keeps it.
                                var folder = Files.createTempDirectory("sui-download-");
                                var file = folder.resolve(
                                        downloadName(response, ctx.trigger().getUrl()));
                                Files.write(file, response.body());
                                onFxThread(() -> downloadHandler.accept(file.toFile()));
                            } catch (Exception e) {
                                reportError(e);
                            }
                        }));

        registerBehavior(UiTrigger.Behavior.UPLOAD.name(), this::uploadBehavior);

        registerBehavior(UiTrigger.Behavior.STREAM.name(), this::streamBehavior);
    }

    // ── streaming ─────────────────────────────────────────────────────────

    /**
     * Registers (or replaces) a handler for one SSE event name. Handlers run
     * on the FX thread and may touch the scene graph.
     */
    public SuiFxEventBus onStreamEvent(String name, FxStreamEventHandler handler) {
        if (name != null && handler != null) streamEventHandlers.put(name, handler);
        return this;
    }

    /** The streams this bus is reading, running or finished. */
    public Collection<FxStreamHandle> activeStreams() {
        return java.util.Collections.unmodifiableCollection(streams.values());
    }

    /**
     * Built-in {@code STREAM} behaviour: opens the trigger's url as an SSE
     * stream and feeds each event to the handler registered under its name.
     *
     * <p>Fire-and-forget on purpose. The returned stage completes as soon as
     * the response headers are in, not when the stream ends — a button that
     * starts a five-minute agent run should stop spinning once the run has
     * <em>started</em>, and the user must be free to navigate away meanwhile.
     * The reader keeps going until the server closes it or
     * {@link FxStreamHandle#abort()} is called.
     */
    private CompletionStage<?> streamBehavior(FxTriggerContext ctx) {
        var trigger = ctx.trigger();
        var builder = HttpRequest.newBuilder(URI.create(renderer.resolve(trigger.getUrl())))
                .header("Accept", "text/event-stream");

        // A stream is a POST by default: it usually carries the prompt or the
        // form that starts the work. Same default the SPA uses.
        var method = trigger.getMethod() == null ? "POST" : trigger.getMethod().toUpperCase();
        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            try {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(ctx.payload())));
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        reportError(new IllegalStateException(
                                "STREAM " + trigger.getUrl() + " failed: HTTP " + response.statusCode()));
                        return;
                    }
                    consume(response, header(response, "Sui-Stream-Channel", null),
                            header(response, "Sui-Stream-Label", "Agent"),
                            header(response, "Sui-Stream-Return-Href", trigger.getUrl()));
                })
                .whenComplete((ok, err) -> {
                    if (err != null) reportError(err);
                });
    }

    /**
     * Reads an SSE body off the IO pool and dispatches its events.
     *
     * <p>{@link HttpResponse.BodyHandlers#ofLines()} hands back a lazy stream
     * of lines, so consuming it <em>is</em> reading the socket — which is why
     * this runs on the IO executor and never on the FX thread.
     */
    private FxStreamHandle consume(HttpResponse<java.util.stream.Stream<String>> response,
                                   String channelId, String label, String returnHref) {

        var lines = response.body();
        var id = channelId != null ? channelId : "sse-" + UUID.randomUUID();
        var handle = new FxStreamHandle(id, label, returnHref, lines::close);
        streams.put(id, handle);

        io.execute(() -> {
            try {
                var block = new ArrayList<String>();
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    var line = iterator.next();
                    // A blank line ends an event; anything before it belongs to it.
                    if (line.isEmpty()) {
                        dispatchSseBlock(block, handle);
                        block.clear();
                    } else {
                        block.add(line);
                    }
                }
                dispatchSseBlock(block, handle);   // a final event with no trailing blank
                if (handle.state() == FxStreamHandle.State.RUNNING) {
                    handle.state(FxStreamHandle.State.COMPLETED);
                }
            } catch (Exception e) {
                // An abort closes the body mid-read and lands here; so does a
                // dropped connection. Neither is worth an error dialog.
                handle.state(FxStreamHandle.State.ERRORED);
            } finally {
                lines.close();
            }
        });
        return handle;
    }

    /** Parses one {@code event:}/{@code data:}/{@code id:} block and routes it. */
    private void dispatchSseBlock(List<String> block, FxStreamHandle handle) {
        if (block.isEmpty()) return;

        var event = "message";
        var data = new StringBuilder();
        for (String line : block) {
            if (line.startsWith(":")) continue;                  // comment / keep-alive
            if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                data.append(line.substring(5).stripLeading());
            } else if (line.startsWith("id:")) {
                try {
                    // The server publishes the channel's monotonic seq here, so
                    // tracking it is what lets a reconnect skip what we saw.
                    handle.seen(Long.parseLong(line.substring(3).trim()));
                } catch (NumberFormatException ignored) {
                    // A non-numeric id is not ours to interpret.
                }
            }
        }
        var handler = streamEventHandlers.get(event);
        if (handler == null) return;
        var payload = data.toString();
        onFxThread(() -> {
            try {
                handler.handle(payload, handle);
            } catch (Exception e) {
                reportError(e);
            }
        });
    }

    /**
     * Re-attaches to the streams a page says are still running and this bus is
     * not already reading — after a restart, or in a second window. The server
     * replays from its ring buffer, so {@code lastSeq=0} asks for everything it
     * still has.
     *
     * <p>Quiet on failure: a resume url that 404s means the stream finished
     * between the page being rendered and this call, and the next page will
     * simply not list it.
     */
    public void reconnectMissingStreams(List<UiPage.ActiveStream> entries) {
        if (entries == null) return;
        for (var entry : entries) {
            if (entry.getChannelId() == null || entry.getResumeUrl() == null) continue;
            if (streams.containsKey(entry.getChannelId())) continue;

            var url = entry.getResumeUrl() + (entry.getResumeUrl().contains("?") ? "&" : "?") + "lastSeq=0";
            var request = HttpRequest.newBuilder(URI.create(renderer.resolve(url)))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            response.body().close();
                            return;
                        }
                        consume(response, entry.getChannelId(),
                                entry.getLabel() == null ? "Agent" : entry.getLabel(),
                                entry.getReturnHref());
                    })
                    .exceptionally(err -> null);
        }
    }

    private static String header(HttpResponse<?> response, String name, String fallback) {
        return response.headers().firstValue(name).orElse(fallback);
    }

    /** {@code filename="report.pdf"} and its {@code filename*=UTF-8''…} twin. */
    private static final java.util.regex.Pattern ATTACHMENT_NAME = java.util.regex.Pattern.compile(
            "filename\\*?=(?:UTF-8'')?[\"']?([^\"';]+)[\"']?", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * What to call a downloaded file: {@code Content-Disposition} if the server
     * said, otherwise the last segment of the url — the same two-step the
     * browser renderer makes, so the same click saves the same name on either.
     */
    private static String downloadName(HttpResponse<?> response, String url) {
        var matcher = ATTACHMENT_NAME.matcher(header(response, "Content-Disposition", ""));
        if (matcher.find()) return safeName(decode(matcher.group(1)));

        var path = url == null ? "" : url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int slash = path.lastIndexOf('/');
        return safeName(decode(slash >= 0 ? path.substring(slash + 1) : path));
    }

    private static String decode(String value) {
        if (value == null || value.indexOf('%') < 0) return value;
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notEscaped) {
            return value;
        }
    }

    /**
     * A name a server chose is not allowed to say where the file goes: strip it
     * to a bare file name, so {@code ../../.ssh/authorized_keys} cannot come
     * back as a path.
     */
    private static String safeName(String name) {
        if (name == null) return "download";
        var cleaned = name.trim().replace('\\', '/').replaceAll("[\\p{Cntrl}]", "");
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) cleaned = cleaned.substring(slash + 1);
        return cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned) ? "download" : cleaned;
    }

    /**
     * The default {@link #setDownloadHandler(Consumer) download handler}: asks
     * where the file should go and puts it there.
     *
     * <p>A browser drops a download into a folder everyone knows and shows it
     * in a list. A desktop window has neither, so it asks — which also makes
     * the download visible, and a download nobody can see is one that did not
     * happen as far as the person who clicked is concerned.
     */
    private void saveAs(File downloaded) {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save " + downloaded.getName());
        chooser.setInitialFileName(downloaded.getName());
        var downloads = new File(System.getProperty("user.home", "."), "Downloads");
        if (downloads.isDirectory()) chooser.setInitialDirectory(downloads);

        var target = chooser.showSaveDialog(frontWindow(null));
        if (target == null) return;   // the user said no; nothing to report
        try {
            Files.copy(downloaded.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            reportError(e);
        }
    }

    /**
     * The window to hang a chooser or a dialog off: the focused one when there
     * is one, so a second window of the same app cannot answer for it.
     */
    private static Window frontWindow(Window exclude) {
        return Window.getWindows().stream()
                .filter(w -> w.isShowing() && w != exclude)
                .min(java.util.Comparator.comparing(w -> w.isFocused() ? 0 : 1))
                .orElse(null);
    }

    /**
     * Built-in {@code UPLOAD} behaviour: POSTs the picked files to the
     * trigger's url as {@code multipart/form-data} and routes the response
     * like any other. Fired by a {@link UiUpload} zone or a {@code FILE}
     * field's change.
     *
     * <p>The multipart field name follows the web renderer: the upload's
     * {@code name}, falling back to its id, and to {@code "files"} for
     * anything else.
     */
    private CompletionStage<?> uploadBehavior(FxTriggerContext ctx) {
        if (ctx.files().isEmpty()) return null;

        var boundary = "sui-fx-" + UUID.randomUUID();
        var method = ctx.trigger().getMethod() == null
                || "GET".equalsIgnoreCase(ctx.trigger().getMethod())
                ? "POST"
                : ctx.trigger().getMethod().toUpperCase();

        byte[] body;
        try {
            body = multipart(ctx.files(), fieldName(ctx.source()), boundary);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        var request = HttpRequest.newBuilder(URI.create(renderer.resolve(ctx.trigger().getUrl())))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> onFxThread(
                        () -> applyResponse(response.body(), ctx.trigger().getUrl())));
    }

    /** The multipart field name for an upload source. */
    private static String fieldName(UiNode source) {
        if (source instanceof UiUpload upload) {
            if (upload.getName() != null) return upload.getName();
            if (upload.getId() != null) return upload.getId();
        }
        // A FILE field posts under its own id, the way its <input name> would.
        if (source != null && source.getId() != null) return source.getId();
        return "files";
    }

    /** Assembles a {@code multipart/form-data} body by hand — HttpClient has none. */
    private static byte[] multipart(List<File> files, String fieldName, String boundary)
            throws IOException {

        var out = new ByteArrayOutputStream();
        for (File file : files) {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + fieldName
                    + "\"; filename=\"" + file.getName() + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            var contentType = Files.probeContentType(file.toPath());
            out.write(("Content-Type: " + (contentType == null ? "application/octet-stream" : contentType)
                    + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(Files.readAllBytes(file.toPath()));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Routes a JSON response body the way the SPA's default response handler
     * does: a {@code UiPatch} is applied, a full {@code UiNode} remounts.
     */
    private void applyResponse(String body, String sourceUrl) {
        if (body == null || body.isBlank()) return;
        try {
            var tree = mapper.readTree(body);
            // Type first: a UiPage carries toasts too, so testing for those
            // ahead of the discriminator would read a whole page as a patch
            // and drop its node on the floor.
            if ("page".equals(tree.path("type").asText(null))) {
                // Only a page moves the base, exactly as in a browser: a
                // navigation changes document.baseURI, an in-place patch does
                // not, or a POST to some endpoint would rebase the whole tree.
                applyPage(mapper.treeToValue(tree, UiPage.class), sourceUrl);
            } else if (tree.has("patches") || tree.has("toasts")) {
                applyPatch(mapper.treeToValue(tree, UiPatch.class));
            } else if (tree.has("type")) {
                var node = mapper.treeToValue(tree, UiNode.class);
                var id = node.getId();
                if (id != null && renderer.context() != null && renderer.context().byId(id) != null) {
                    applyPatch(UiPatch.of().patch(UiPatch.Operation.replace(id, node)));
                } else {
                    // Nothing to patch into — hand it to the app to remount.
                    reportError(new IllegalStateException(
                            "Response node '" + id + "' has no counterpart in the mounted tree; "
                                    + "call mount() yourself for a full replacement"));
                }
            }
        } catch (Exception e) {
            reportError(e);
        }
    }

    /** Sends a trigger's request and hands back the body alone. */
    private <T> java.util.concurrent.CompletableFuture<T> sendAsync(
            FxTriggerContext ctx, HttpResponse.BodyHandler<T> bodyHandler) {
        return exchange(ctx, bodyHandler).thenApply(HttpResponse::body);
    }

    /**
     * The same request, response and all — a download needs the headers to
     * learn what the file is called.
     */
    private <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> exchange(
            FxTriggerContext ctx, HttpResponse.BodyHandler<T> bodyHandler) {

        var trigger = ctx.trigger();
        var builder = HttpRequest.newBuilder(URI.create(renderer.resolve(trigger.getUrl())));
        var method = trigger.getMethod() == null ? "GET" : trigger.getMethod().toUpperCase();

        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            String json;
            try {
                json = mapper.writeValueAsString(ctx.payload());
            } catch (Exception e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        builder.header("Accept", "application/json");

        return http.sendAsync(builder.build(), bodyHandler)
                .whenComplete((ok, err) -> {
                    if (err != null) reportError(err);
                });
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static void onFxThread(Runnable work) {
        if (Platform.isFxApplicationThread()) work.run();
        else Platform.runLater(work);
    }

    private static void defaultToast(UiToast toast) {
        var type = switch (toast.getLevel() == null ? UiToast.Level.INFO : toast.getLevel()) {
            case ERROR -> Alert.AlertType.ERROR;
            case WARN -> Alert.AlertType.WARNING;
            default -> Alert.AlertType.INFORMATION;
        };
        var alert = new Alert(type, toast.getMessage() == null ? "" : toast.getMessage());
        if (toast.getTitle() != null) alert.setHeaderText(toast.getTitle());
        alert.show();
    }

    private static void browse(String url) {
        try {
            if (url != null && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            System.getLogger(SuiFxEventBus.class.getName())
                    .log(System.Logger.Level.ERROR, "SuiFxEventBus: cannot open " + url, e);
        }
    }
}
