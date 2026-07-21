package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiMenuButton;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiSpinner;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.model.UiTrigger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Renders real trees through the real renderer. The JavaFX twin of
 * {@code SuiServerRendererTest}: it locks the structural promises the other two
 * renderers make — one payload per form no matter the nesting, tabs painted
 * eagerly, INVOKE reaching a plain Java method.
 *
 * <p>Runs headless via the Monocle-less software pipeline; the toolkit is
 * started once for the whole class.
 */
class SuiFxRendererTest {

    @BeforeAll
    static void startToolkit() {
        // Software pipeline: no GPU needed, but a windowing system still is.
        // OpenJFX ships no headless glass backend (that is Monocle, a separate
        // artifact), so on a display-less machine these tests are skipped
        // rather than failed.
        System.setProperty("prism.order", "sw");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Another test class got there first — fine.
        } catch (Throwable noDisplay) {
            assumeTrue(false, "No JavaFX toolkit available here: " + noDisplay);
        }
    }

    @Test
    void formCollectsEveryFieldRegardlessOfNesting() {
        var form = UiForm.of("customer-form", "Customer")
                .field(UiField.text("name", "Name", "Ada").asEditable())
                .content(UiStack.of(
                        UiField.text("city", "City", "London").asEditable(),
                        UiSection.of("details", null)
                                .section("billing", "Billing",
                                        UiField.text("iban", "IBAN", "GB33").asEditable())));

        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.mount(form));

        // The nested field, the tab-buried field and the flat one all ride in
        // the same payload — the promise the web renderers make too.
        var payload = capturePayload(bus, "save",
                () -> bus.dispatch(UiTrigger.invoke("save", "customer-form"), form, bus.context()));

        assertThat(payload)
                .containsEntry("name", "Ada")
                .containsEntry("city", "London")
                .containsEntry("iban", "GB33");
    }

    @Test
    void invokeReachesAPlainJavaHandler() throws Exception {
        var action = UiAction.primary("go", "Go").onClick(UiTrigger.invoke("ping"));
        var bus = new SuiFxEventBus();
        var called = new CountDownLatch(1);

        onFxThread(() -> {
            bus.registerClientHandler("ping", ctx -> called.countDown());
            bus.mount(action);
            bus.dispatch(action.getOnClick(), action, bus.context());
            return null;
        });

        // Handlers are asynchronous, so wait for it rather than assuming it
        // already ran by the time dispatch returned.
        assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void theOverlayHostPatternMountsPatchesAndAutoWiresToasts() {
        // The documented pattern: overlay is the host, the renderer paints into
        // it, the bus drives the renderer. No setOverlay / setToastHandler call.
        SuiFxOverlay overlay = onFxThread(() -> new SuiFxOverlay());
        var renderer = new SuiFxRenderer().attach(overlay);
        var bus      = new SuiFxEventBus(renderer);

        var panel = UiStack.of(UiText.of("before"));
        panel.setId("panel");
        onFxThread(() -> renderer.mount(UiStack.of(panel)));

        // mount put the tree inside the overlay, and the id index found it.
        Node found = onFxThread(() -> renderer.context().byId("panel"));
        assertThat(found).isNotNull();

        // A patch with a toast: the node op is applied AND the toast lands on
        // the overlay, purely because the renderer was attached to it.
        var replacement = UiStack.of(UiText.of("after"));
        replacement.setId("panel");
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of()
                    .patch(UiPatch.Operation.replace("panel", replacement))
                    .toast(UiToast.success("done")));
            return null;
        });

        String toastText = onFxThread(() -> textOf(overlay.toastPane()));
        assertThat(toastText).contains("done");
        String panelText = onFxThread(() -> textOf(renderer.context().byId("panel")));
        assertThat(panelText).contains("after").doesNotContain("before");
    }

    /** All the label text anywhere under a node, joined — a crude but handy probe. */
    private static String textOf(Node root) {
        if (root instanceof Label l) return l.getText() == null ? "" : l.getText();
        if (root instanceof javafx.scene.Parent p) {
            var sb = new StringBuilder();
            for (var child : p.getChildrenUnmodifiable()) sb.append(textOf(child)).append(' ');
            return sb.toString();
        }
        return "";
    }

    @Test
    void aMenuKeepsLongLabelsSubmenusAndBadgesInside() {
        // The menu has a fixed width, so anything wider than it — a long label,
        // a badge pushed to the right, a submenu's title + arrow — has to
        // truncate rather than spill past the edge.
        var menu = UiMenu.of("nav", "Sections",
                UiMenuItem.of("inbox", "Inbox with a really quite long label").badge("128"),
                UiMenuItem.of("archive", "Archive"),
                UiMenuItem.group("settings", "Settings and other preferences",
                        UiMenuItem.of("profile", "Profile"),
                        UiMenuItem.of("billing", "Billing")).open(true));

        double overflow = onFxThread(() -> layoutStandalone(menu));

        // A few px of rounding is fine; a spilling submenu overran by tens.
        assertThat(overflow).isLessThan(2.0);
    }

    @Test
    void railCollapsesTheMenuButKeepsTheToggleReachable() {
        // No icons in this renderer, so a narrow "rail" could only show
        // truncated labels. RAIL therefore collapses the menu — but the toggle
        // has to survive, or it could never be opened again.
        var menu = UiMenu.of("nav", "Sections",
                        UiMenuItem.of("inbox", "Inbox"),
                        UiMenuItem.of("archive", "Archive"))
                .state(UiMenu.State.RAIL)
                .toggle(true);

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(menu));
        var box = (VBox) painted;

        assertThat(box.isVisible()).as("the menu itself stays, for the toggle").isTrue();
        // Children are [toggle, body]; the body is the collapsible part.
        var body = box.getChildren().get(1);
        assertThat(body.isVisible()).as("items are collapsed away").isFalse();
        assertThat(body.isManaged()).as("and take no layout space").isFalse();
        var toggle = (javafx.scene.control.Button) box.getChildren().get(0);
        assertThat(toggle.getText()).contains("☰");
    }

    @Test
    void railWithoutAToggleRemovesTheMenuEntirely() {
        // Nothing would be left to click, so the whole node goes.
        var menu = UiMenu.of("nav", "Sections", UiMenuItem.of("inbox", "Inbox"))
                .state(UiMenu.State.RAIL);

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(menu));

        assertThat(painted.isVisible()).isFalse();
        assertThat(painted.isManaged()).isFalse();
    }

    /** Lays a node out on its own and returns how far its content overruns it. */
    private static double layoutStandalone(ai.mindconnect.ui.model.UiNode model) {
        var node = (javafx.scene.layout.Region) new SuiFxEventBus().mount(model);
        new javafx.scene.Scene(node);
        node.applyCss();
        node.autosize();      // the menu pins its own width; height follows
        node.layout();
        double right = node.localToScene(0, 0).getX() + node.getWidth();
        return maxDescendantRight(node) - right;
    }

    /** The right-most scene x of any descendant — how far content actually reaches. */
    private static double maxDescendantRight(javafx.scene.Parent root) {
        double max = Double.NEGATIVE_INFINITY;
        for (Node c : root.getChildrenUnmodifiable()) max = Math.max(max, sceneRight(c));
        return max;
    }

    private static double sceneRight(Node n) {
        double r = n.localToScene(n.getBoundsInLocal()).getMaxX();
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) r = Math.max(r, sceneRight(c));
        }
        return r;
    }

    @Test
    void paintsTextTableAndTabs() {
        var table = UiTable.of("orders", "Orders")
                .column(ai.mindconnect.ui.model.UiColumn.text("id", "Id"))
                .row(Map.of("id", "1"));
        var tabs = UiSection.of("main", null)
                .section("first", "First", UiText.of("hello"))
                .section("second", "Second", table);

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(tabs));

        assertThat(painted).isInstanceOf(TabPane.class);
        var pane = (TabPane) painted;
        assertThat(pane.getTabs()).hasSize(2);
        // Every panel scrolls: a form taller than the window must stay
        // reachable on the desktop, the way a page scrolls in the browser.
        assertThat(pane.getTabs().get(0).getContent()).isInstanceOf(ScrollPane.class);
        assertThat(((ScrollPane) pane.getTabs().get(0).getContent()).isFitToWidth()).isTrue();

        // Eagerly painted: the unselected tab already holds its content, which
        // is what keeps its fields in the form payload.
        assertThat(panel(pane, 0)).isInstanceOf(Label.class);
        assertThat(((VBox) panel(pane, 1)).getChildren())
                .anyMatch(TableView.class::isInstance);
    }

    @Test
    void aRowActionArrivesWithItsRowAsPayload() {
        var row = ai.mindconnect.ui.model.UiRow.of(Map.of("id", "1001", "product", "Keyboard"));
        var bus = new SuiFxEventBus();

        // No payload id on the trigger: the nearest payload is the row itself,
        // which is what makes row actions usable without naming anything.
        var payload = capturePayload(bus, "delete",
                () -> bus.dispatch(UiTrigger.invoke("delete"), row, bus.context()));

        assertThat(payload).containsEntry("id", "1001").containsEntry("product", "Keyboard");
    }

    @Test
    void paintsATreeWithItsModelStateApplied() {
        var tree = ai.mindconnect.ui.model.UiTree.of("catalog", null)
                .node(ai.mindconnect.ui.model.UiTreeNode.of("hardware", "Hardware").open(true)
                        .child(ai.mindconnect.ui.model.UiTreeNode.of("kb", "Keyboards")))
                .node(ai.mindconnect.ui.model.UiTreeNode.of("services", "Services"));

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(tree));

        assertThat(painted).isInstanceOf(javafx.scene.control.TreeView.class);
        var view = (javafx.scene.control.TreeView<?>) painted;
        // Root is hidden so the model's top level stays the visible top level.
        assertThat(view.isShowRoot()).isFalse();
        assertThat(view.getRoot().getChildren()).hasSize(2);
        assertThat(view.getRoot().getChildren().get(0).isExpanded()).isTrue();
        assertThat(view.getRoot().getChildren().get(0).getChildren()).hasSize(1);
    }

    @Test
    void paintsASpinner() {
        Node painted = onFxThread(() -> new SuiFxEventBus().mount(UiSpinner.of()));

        assertThat(painted).isInstanceOf(ProgressIndicator.class);
        assertThat(((ProgressIndicator) painted).getProgress())
                .isEqualTo(ProgressIndicator.INDETERMINATE_PROGRESS);
    }

    @Test
    void aBusyDispatchMarksTheClickedControlAndClearsWhenTheStageCompletes() {
        var action = UiAction.primary("go", "Go").onClick(UiTrigger.invoke("slow"));
        var bus = new SuiFxEventBus();
        var pending = new CompletableFuture<Void>();
        // A behaviour that is still running: the busy state must outlive the
        // handler call and end only when the stage completes.
        bus.registerBehavior(UiTrigger.Behavior.INVOKE.name(), ctx -> pending);

        Node button = onFxThread(() -> {
            var painted = bus.mount(action);
            bus.dispatch(action.getOnClick(), action, bus.context());
            return painted;
        });

        assertThat(button.getStyleClass()).contains(SuiFxEventBus.LOADING_CLASS);
        assertThat(button.isDisable()).isTrue();

        onFxThread(() -> pending.complete(null));
        // The completion hops back through Platform.runLater; let it land.
        onFxThread(() -> null);

        assertThat(button.getStyleClass()).doesNotContain(SuiFxEventBus.LOADING_CLASS);
        assertThat(button.isDisable()).isFalse();
    }

    @Test
    void anFxThreadHandlerIsNeverLeftBusy() {
        var action = UiAction.primary("go", "Go").onClick(UiTrigger.invoke("quick"));
        var bus = new SuiFxEventBus();

        Node button = onFxThread(() -> {
            // FX-thread handlers are the synchronous path: by the time dispatch
            // returns the work is done, so the busy state must already be gone.
            bus.registerClientHandler("quick", ctx -> { }, FxHandlerThread.FX);
            var painted = bus.mount(action);
            bus.dispatch(action.getOnClick(), action, bus.context());
            return painted;
        });

        assertThat(button.getStyleClass()).doesNotContain(SuiFxEventBus.LOADING_CLASS);
        assertThat(button.isDisable()).isFalse();
    }

    @Test
    void aFailingBehaviourStillClearsTheBusyState() {
        var action = UiAction.primary("go", "Go").onClick(UiTrigger.invoke("nope"));
        var bus = new SuiFxEventBus();
        var errors = new AtomicReference<Throwable>();

        Node button = onFxThread(() -> {
            bus.setOnError(errors::set);
            // No client handler registered for "nope" — the behaviour throws.
            var painted = bus.mount(action);
            bus.dispatch(action.getOnClick(), action, bus.context());
            return painted;
        });

        assertThat(errors.get()).isInstanceOf(IllegalStateException.class);
        assertThat(button.isDisable()).isFalse();
        assertThat(button.getStyleClass()).doesNotContain(SuiFxEventBus.LOADING_CLASS);
    }

    @Test
    void toastsFromAPatchLandInTheOverlay() {
        var bus = new SuiFxEventBus();

        SuiFxOverlay overlay = onFxThread(() -> {
            var layer = new SuiFxOverlay(bus.mount(UiText.of("content")));
            bus.setOverlay(layer);
            bus.applyPatch(UiPatch.of().toast(UiToast.success("Saved").title("Customer")));
            return layer;
        });

        assertThat(overlay.toastPane().getChildren()).hasSize(1);
        assertThat(overlay.toastPane().getChildren().get(0).getStyleClass())
                .contains("sui-toast", "sui-toast-success");
    }

    @Test
    void aClientHandlerRunsOffTheFxThreadAndStaysBusyUntilItIsDone() throws Exception {
        var action = UiAction.primary("go", "Go").onClick(UiTrigger.invoke("slow"));
        var bus = new SuiFxEventBus();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ranOnFxThread = new AtomicReference<Boolean>();

        Node button = onFxThread(() -> {
            bus.registerClientHandler("slow", ctx -> {
                ranOnFxThread.set(Platform.isFxApplicationThread());
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
            });
            var painted = bus.mount(action);
            bus.dispatch(action.getOnClick(), action, bus.context());
            return painted;
        });

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        // The whole point: the handler is off-thread, so the window kept
        // running while it worked — and the button is still marked busy.
        assertThat(ranOnFxThread.get()).isFalse();
        assertThat(button.isDisable()).isTrue();
        assertThat(button.getStyleClass()).contains(SuiFxEventBus.LOADING_CLASS);

        release.countDown();
        // Two hops: the handler completes onto the FX thread, then the busy
        // state clears there.
        onFxThread(() -> null);
        onFxThread(() -> null);

        assertThat(button.isDisable()).isFalse();
        assertThat(button.getStyleClass()).doesNotContain(SuiFxEventBus.LOADING_CLASS);
    }

    @Test
    void aBackgroundHandlerMayPatchAndDispatch() throws Exception {
        var bus = new SuiFxEventBus();
        var text = UiText.of("target", "before");
        var done = new CountDownLatch(1);

        onFxThread(() -> {
            // applyPatch from a background thread must get itself onto the FX
            // thread rather than corrupting the scene graph.
            bus.registerClientHandler("patchIt", ctx -> {
                bus.applyPatch(UiPatch.of().patch(
                        UiPatch.Operation.replace("target", UiText.of("target", "after"))));
                done.countDown();
            });
            bus.mount(UiStack.of(text));
            bus.dispatch(UiTrigger.invoke("patchIt"), text, bus.context());
            return null;
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        onFxThread(() -> null);

        Node patched = onFxThread(() -> bus.context().byId("target"));
        assertThat(((Label) patched).getText()).isEqualTo("after");
    }

    @Test
    void anFxRegisteredHandlerStaysOnTheFxThread() {
        var bus = new SuiFxEventBus();
        var onFx = new AtomicReference<Boolean>();

        onFxThread(() -> {
            bus.registerClientHandler("inline",
                    ctx -> onFx.set(Platform.isFxApplicationThread()),
                    FxHandlerThread.FX);
            bus.dispatch(UiTrigger.invoke("inline"), UiText.of("x"), bus.context());
            return null;
        });

        assertThat(onFx.get()).isTrue();
    }

    @Test
    void paintsAMenuWithGroupsAndItems() {
        var menu = UiMenu.of("nav", null,
                UiMenuItem.of("inbox", "Inbox").badge("3").selected(true),
                UiMenuItem.divider(),
                UiMenuItem.group("settings", "Settings",
                        UiMenuItem.of("profile", "Profile")));

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(menu));

        // The items live in the collapsible body, not directly in the menu —
        // that split is what lets RAIL hide them while keeping the toggle.
        var body = (VBox) ((VBox) painted).getChildren().get(0);
        var children = body.getChildren();
        // badge → row, divider → separator, group → titled pane
        assertThat(children).hasSize(3);
        assertThat(children.get(1)).isInstanceOf(javafx.scene.control.Separator.class);
        assertThat(children.get(2)).isInstanceOf(javafx.scene.control.TitledPane.class);
    }

    @Test
    void paintsAMenuButtonWithASubmenu() {
        var button = UiMenuButton.of("more",
                        UiMenuItem.of("export", "Export"),
                        UiMenuItem.divider(),
                        UiMenuItem.group("reports", "Reports", UiMenuItem.of("monthly", "Monthly")))
                .label("More…");

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(button));

        assertThat(painted).isInstanceOf(javafx.scene.control.MenuButton.class);
        var items = ((javafx.scene.control.MenuButton) painted).getItems();
        assertThat(items).hasSize(3);
        assertThat(items.get(1)).isInstanceOf(javafx.scene.control.SeparatorMenuItem.class);
        assertThat(items.get(2)).isInstanceOf(javafx.scene.control.Menu.class);
    }

    @Test
    void paintsProgressDeterminateAndIndeterminate() {
        Node determinate = onFxThread(() ->
                new SuiFxEventBus().mount(ai.mindconnect.ui.model.UiProgress.of(2, 4)));
        Node indeterminate = onFxThread(() ->
                new SuiFxEventBus().mount(ai.mindconnect.ui.model.UiProgress.indeterminate()));

        assertThat(((javafx.scene.control.ProgressBar) determinate).getProgress()).isEqualTo(0.5);
        assertThat(((javafx.scene.control.ProgressBar) indeterminate).getProgress())
                .isEqualTo(ProgressIndicator.INDETERMINATE_PROGRESS);
    }

    @Test
    void anExternalLinkOpensInTheBrowserRatherThanNavigating() {
        var link = ai.mindconnect.ui.model.UiLink.external("docs", "https://example.com", "Docs");
        var bus = new SuiFxEventBus();
        var opened = new AtomicReference<String>();

        onFxThread(() -> {
            bus.setLinkOpener(opened::set);
            var painted = (javafx.scene.control.Hyperlink) bus.mount(link);
            painted.fire();
            return null;
        });

        assertThat(opened.get()).isEqualTo("https://example.com");
    }

    @Test
    void repeatedPatchesOfTheSameIdKeepFindingTheirTarget() throws Exception {
        var bus = new SuiFxEventBus();
        var done = new CountDownLatch(1);
        var errors = new AtomicReference<Throwable>();

        onFxThread(() -> {
            // A patch whose target is missing reports rather than throws, so
            // watch the error handler from the start — otherwise a silently
            // lost target would still look like a pass.
            bus.setOnError(errors::set);
            // What a progress-reporting long task does: replace the same node
            // over and over. Each replacement has to be re-indexed, or round
            // two would patch into a node that is no longer in the scene.
            bus.registerClientHandler("report", ctx -> {
                for (int step = 1; step <= 5; step++) {
                    bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace(
                            "progress", UiText.of("progress", "step " + step))));
                }
                done.countDown();
            });
            bus.mount(UiStack.of(UiText.of("progress", "step 0")));
            bus.dispatch(UiTrigger.invoke("report"), UiText.of("x"), bus.context());
            return null;
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        onFxThread(() -> null);

        assertThat(errors.get()).isNull();
        assertThat(((Label) onFxThread(() -> bus.context().byId("progress"))).getText())
                .isEqualTo("step 5");
    }

    @Test
    void aDetailShowsAnEmDashForMissingValues() {
        var detail = ai.mindconnect.ui.model.UiDetail.of("d", null)
                .field(UiField.text("name", "Name", "Ada"))
                .field(UiField.text("shared", "Shared with", null));

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(detail));

        var grid = (javafx.scene.layout.GridPane) ((VBox) painted).getChildren().get(0);
        var values = grid.getChildren().stream()
                .filter(n -> GridPane.getColumnIndex(n) != null && GridPane.getColumnIndex(n) == 1)
                .map(n -> ((Label) n).getText())
                .toList();
        // A missing value keeps its row: "not set" is information, and a
        // collapsed row would silently hide the field.
        assertThat(values).containsExactly("Ada", "—");
    }

    @Test
    void aListPaintsItemsAndCollapsesTheOnesThatSaySo() {
        var list = ai.mindconnect.ui.model.UiList.of("docs", null)
                .item(ai.mindconnect.ui.model.UiList.Item.of("a", "Plain").description("desc"))
                .item(ai.mindconnect.ui.model.UiList.Item.of("b", "Collapsible")
                        .collapsible("Collapsible", false)
                        .content(UiText.of("body")));

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(list));

        var items = (VBox) ((VBox) painted).getChildren().get(0);
        assertThat(items.getChildren().get(0)).isInstanceOf(VBox.class);
        var collapsible = items.getChildren().get(1);
        assertThat(collapsible).isInstanceOf(javafx.scene.control.TitledPane.class);
        assertThat(((javafx.scene.control.TitledPane) collapsible).isExpanded()).isFalse();
    }

    @Test
    void anUploadZoneHandsItsFilesToTheTrigger() throws Exception {
        var upload = ai.mindconnect.ui.model.UiUpload.of("import", "Import")
                .onUpload(UiTrigger.invoke("readFile"));
        var bus = new SuiFxEventBus();
        var file = java.nio.file.Files.createTempFile("sui-upload-", ".txt");
        java.nio.file.Files.writeString(file, "hello");
        var received = new AtomicReference<java.util.List<java.io.File>>();
        var done = new CountDownLatch(1);

        onFxThread(() -> {
            bus.registerClientHandler("readFile", ctx -> {
                received.set(ctx.files());
                done.countDown();
            });
            bus.mount(upload);
            // What a drop or a file-picker choice ends up doing.
            bus.dispatch(upload.getOnUpload(), upload, bus.context(), List.of(file.toFile()));
            return null;
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        // The local path: the handler gets real Files and reads them itself —
        // no server, no multipart.
        assertThat(received.get()).hasSize(1);
        assertThat(java.nio.file.Files.readString(received.get().get(0).toPath())).isEqualTo("hello");
        java.nio.file.Files.deleteIfExists(file);
    }

    @Test
    void theUploadBehaviourPostsMultipart() throws Exception {
        var file = java.nio.file.Files.createTempFile("sui-upload-", ".txt");
        java.nio.file.Files.writeString(file, "file-content");

        // A real socket: multipart framing is exactly the kind of thing that
        // looks right in the code and is wrong on the wire.
        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        var received = new CountDownLatch(1);
        server.createContext("/files", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            received.countDown();
        });
        server.start();

        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/files";
            var upload = ai.mindconnect.ui.model.UiUpload.of("docs", "Docs")
                    .name("attachment")
                    .uploadTo(url);
            var bus = new SuiFxEventBus();

            onFxThread(() -> {
                bus.mount(upload);
                bus.dispatch(upload.getOnUpload(), upload, bus.context(), List.of(file.toFile()));
                return null;
            });

            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");

            // Assert the framing, not just that the pieces appear somewhere.
            // A body with the right substrings but a missing CRLF is still
            // unparseable by every real server, and "contains" would not
            // notice.
            var boundary = contentType.get().substring(contentType.get().indexOf("boundary=") + 9);
            assertThat(body.get())
                    .startsWith("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"attachment\"; "
                            + "filename=\"" + file.getFileName() + "\"\r\n"
                            + "Content-Type: ")
                    // The part's content is delimited by a CRLF before the
                    // closing boundary — that CRLF belongs to the framing, not
                    // to the file.
                    .endsWith("\r\n\r\nfile-content\r\n--" + boundary + "--\r\n");
        } finally {
            server.stop(0);
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void unknownTypesPaintAPlaceholderInsteadOfFailing() {
        // UiHeader has no JavaFX renderer yet.
        var header = new ai.mindconnect.ui.model.UiHeader();
        header.setTitle("Page header");

        Node painted = onFxThread(() -> new SuiFxEventBus().mount(header));

        assertThat(painted.getStyleClass()).contains("sui-unsupported");
    }

    /** The painted panel of a tab, unwrapped from its scroll container. */
    private static Node panel(TabPane pane, int index) {
        return ((ScrollPane) pane.getTabs().get(index).getContent()).getContent();
    }

    /**
     * Registers {@code handler} as a client handler, runs {@code dispatch}, and
     * waits for the payload the handler received. Handlers are asynchronous, so
     * every payload assertion has to go through something like this.
     */
    private static Map<String, Object> capturePayload(SuiFxEventBus bus, String handler,
                                                      Runnable dispatch) {
        var captured = new AtomicReference<Map<String, Object>>();
        var done = new CountDownLatch(1);
        onFxThread(() -> {
            bus.registerClientHandler(handler, ctx -> {
                captured.set(ctx.payload());
                done.countDown();
            });
            dispatch.run();
            return null;
        });
        try {
            if (!done.await(5, TimeUnit.SECONDS)) throw new AssertionError("handler never ran");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        return captured.get();
    }

    // ── harness ───────────────────────────────────────────────────────────

    /** Runs {@code work} on the JavaFX application thread and returns its result. */
    private static <T> T onFxThread(Supplier<T> work) {
        var result = new AtomicReference<T>();
        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("FX thread timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        if (error.get() != null) throw new AssertionError(error.get());
        return result.get();
    }

    private static void onFxThread(Runnable work) {
        onFxThread(() -> {
            work.run();
            return null;
        });
    }
}
