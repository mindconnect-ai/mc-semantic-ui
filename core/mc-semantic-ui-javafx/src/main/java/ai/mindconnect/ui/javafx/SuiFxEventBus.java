package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiRow;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.model.UiUpload;
import ai.mindconnect.ui.model.UiTrigger;
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
    private Consumer<File> downloadHandler = file ->
            System.getLogger(SuiFxEventBus.class.getName())
                    .log(System.Logger.Level.INFO, "SuiFxEventBus: downloaded to " + file);

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
        this(renderer, new ObjectMapper());
    }

    public SuiFxEventBus(SuiFxRenderer renderer, ObjectMapper mapper) {
        this.renderer = renderer;
        this.mapper = mapper;
        renderer.setBus(this);
        installDefaultBehaviors();
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
            // Node operations are the renderer's job (it owns the id index);
            // toasts are the bus's, since only it knows the toast handler.
            renderer.applyPatch(patch);
            if (patch.getToasts() != null) patch.getToasts().forEach(toastHandler);
        });
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

        var content = renderer.context().render(dialog);

        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (dialog.getTitle() != null) stage.setTitle(dialog.getTitle());

        var close = new Button("Close");
        close.setOnAction(e -> stage.close());
        var footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(0, 16, 16, 16));

        var root = new VBox(content, footer);
        root.getStyleClass().add("sui-dialog");

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

    /** Gives the dialog the same stylesheets as the window it came from. */
    private void inheritStylesheets(Stage dialogStage) {
        Window owner = Window.getWindows().stream()
                .filter(w -> w.isShowing() && w != dialogStage)
                .findFirst()
                .orElse(null);
        if (owner instanceof Stage ownerStage && ownerStage.getScene() != null) {
            dialogStage.initOwner(ownerStage);
            dialogStage.getScene().getStylesheets().addAll(ownerStage.getScene().getStylesheets());
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
                        .thenAccept(body -> onFxThread(() -> applyResponse(body))));

        registerBehavior(UiTrigger.Behavior.OPEN_IN_TAB.name(), ctx -> {
            linkOpener.accept(ctx.trigger().getUrl());
            return null;
        });

        registerBehavior(UiTrigger.Behavior.DOWNLOAD.name(), ctx ->
                sendAsync(ctx, HttpResponse.BodyHandlers.ofByteArray())
                        .thenAccept(bytes -> {
                            try {
                                var file = Files.createTempFile("sui-download-", "").toFile();
                                Files.write(file.toPath(), bytes);
                                onFxThread(() -> downloadHandler.accept(file));
                            } catch (Exception e) {
                                reportError(e);
                            }
                        }));

        registerBehavior(UiTrigger.Behavior.UPLOAD.name(), this::uploadBehavior);

        registerBehavior(UiTrigger.Behavior.STREAM.name(), ctx -> {
            throw new UnsupportedOperationException(
                    "STREAM is not implemented by the JavaFX renderer yet — "
                            + "register your own behaviour to handle it");
        });
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

        var request = HttpRequest.newBuilder(URI.create(ctx.trigger().getUrl()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> onFxThread(() -> applyResponse(response.body())));
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
    private void applyResponse(String body) {
        if (body == null || body.isBlank()) return;
        try {
            var tree = mapper.readTree(body);
            if (tree.has("patches") || tree.has("toasts")) {
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

    private <T> java.util.concurrent.CompletableFuture<T> sendAsync(
            FxTriggerContext ctx, HttpResponse.BodyHandler<T> bodyHandler) {

        var trigger = ctx.trigger();
        var builder = HttpRequest.newBuilder(URI.create(trigger.getUrl()));
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
                .thenApply(HttpResponse::body)
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
