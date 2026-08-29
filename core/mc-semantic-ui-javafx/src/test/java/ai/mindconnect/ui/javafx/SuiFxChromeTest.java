package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiList;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The chrome around the tree: styling that has to resolve, dialogs that are
 * windows rather than nodes, and the empty strings a real server sends.
 *
 * <p>Every case here came out of pointing the JavaFX client at a live admin UI
 * for the first time.
 */
class SuiFxChromeTest {

    private final List<Stage> opened = new ArrayList<>();

    @BeforeAll
    static void startToolkit() {
        System.setProperty("prism.order", "sw");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Another test class got there first — fine.
        } catch (Throwable noDisplay) {
            assumeTrue(false, "No JavaFX toolkit available here: " + noDisplay);
        }
        // These tests open real windows and close them again. Without this the
        // last close would take the whole toolkit down with it — implicit exit
        // is on by default — and every test after it would hang waiting for an
        // FX thread that is never coming back.
        Platform.setImplicitExit(false);
    }

    @AfterEach
    void closeWindows() {
        onFxThread(() -> {
            List.copyOf(Window.getWindows()).forEach(w -> {
                if (w instanceof Stage stage) stage.close();
            });
            return null;
        });
        opened.clear();
    }

    // ── styling ───────────────────────────────────────────────────────────

    @Test
    void anEmbeddedOverlayStillResolvesThePalette() {
        // The palette is a set of looked-up colours. JavaFX puts the "root"
        // style class on the scene root and nowhere else, so an overlay mounted
        // below one — under a toolbar, in a split pane — used to see none of
        // them, and every colour in the stylesheet silently fell back to the
        // literal name. A whole window came up unstyled.
        var overlay = new SuiFxOverlay();
        var renderer = SuiFxRenderer.createDefaultRenderer(overlay);
        var bus = new SuiFxEventBus(renderer);

        var button = (Button) onFxThread(() -> {
            var root = new BorderPane();
            root.setTop(new Label("chrome of the app's own"));
            root.setCenter(overlay);
            new Scene(root, 800, 600);

            var painted = renderer.mount(UiAction.danger("save", "Save"));
            root.applyCss();
            root.layout();
            return painted;
        });

        assertThat(button.getBackground()).isNotNull();
        assertThat(button.getBackground().getFills()).isNotEmpty();
        assertThat(button.getBackground().getFills().get(0).getFill())
                .as("-sui-danger-bg must resolve, not fall back to the name")
                .isEqualTo(Color.web("#fee2e2"));
        assertThat(button.getTextFill()).isEqualTo(Color.web("#dc2626"));
        assertThat(bus).isNotNull();
    }

    // ── empty strings ─────────────────────────────────────────────────────

    @Test
    void anEmptyStringCountsAsAbsent() {
        // The TS renderers write `node.title ? … : ""`, and "" is falsy in
        // JavaScript. A Java != null check disagrees, and the difference is a
        // blank strip on screen that the browser never draws.
        assertThat(SuiFxText.present(null)).isFalse();
        assertThat(SuiFxText.present("")).isFalse();
        assertThat(SuiFxText.present("   ")).isFalse();
        assertThat(SuiFxText.present("x")).isTrue();

        assertThat(SuiFxText.first(null, "", "title")).isEqualTo("title");
        assertThat(SuiFxText.first("label", "title")).isEqualTo("label");
        assertThat(SuiFxText.first(null, "")).isNull();
    }

    @Test
    void aListWithAnEmptyTitleDrawsNoTitle() {
        var withTitle = UiList.of("a", "Orders");
        var blankTitle = UiList.of("b", "");

        var titled = (javafx.scene.layout.VBox) onFxThread(() ->
                SuiFxRenderer.createDefaultRenderer().mount(withTitle));
        var untitled = (javafx.scene.layout.VBox) onFxThread(() ->
                SuiFxRenderer.createDefaultRenderer().mount(blankTitle));

        assertThat(hasStyleClass(titled, "sui-list-title")).isTrue();
        assertThat(hasStyleClass(untitled, "sui-list-title")).isFalse();
    }

    @Test
    void detailLabelsKeepTheirWidth() {
        // The value column grows always. Without a floor on the term column
        // GridPane squeezes it to nothing and JavaFX renders every label as a
        // bare "..." — the field names disappear from the screen entirely.
        var detail = ai.mindconnect.ui.model.UiDetail.of("d", "Agent")
                .field(ai.mindconnect.ui.model.UiField.text("name", "Name", "document-analyst"))
                .field(ai.mindconnect.ui.model.UiField.text("status", "Status", "ACTIVE"));

        var grid = (GridPane) onFxThread(() -> {
            var painted = SuiFxRenderer.createDefaultRenderer().mount(detail);
            return findGrid(painted);
        });

        assertThat(grid).isNotNull();
        assertThat(grid.getColumnConstraints().get(0).getMinWidth())
                .isEqualTo(javafx.scene.layout.Region.USE_PREF_SIZE);
        assertThat(grid.getColumnConstraints().get(1).getHgrow())
                .isEqualTo(javafx.scene.layout.Priority.ALWAYS);
    }

    // ── visibility ────────────────────────────────────────────────────────

    @Test
    void hiddenTakesTheNodeOutOfTheLayout() {
        // The state has been on UiNode since v0.1.3 and this renderer never
        // honoured it: the web folds it into a css class, JavaFX cannot style
        // visible or managed, so the class went on and nothing read it.
        var text = UiText.of("filters", "Filters");
        text.setDisplay(ai.mindconnect.ui.model.UiNode.Display.HIDDEN);

        var painted = (Label) onFxThread(() ->
                SuiFxRenderer.createDefaultRenderer().mount(UiStack.of(text)).lookup("#filters"));

        assertThat(painted.isVisible()).isFalse();
        assertThat(painted.isManaged()).as("HIDDEN is display:none — out of the layout").isFalse();
    }

    @Test
    void blankHidesTheNodeButKeepsItsSpace() {
        var text = UiText.of("filters", "Filters");
        text.setDisplay(ai.mindconnect.ui.model.UiNode.Display.BLANK);

        var painted = (Label) onFxThread(() ->
                SuiFxRenderer.createDefaultRenderer().mount(UiStack.of(text)).lookup("#filters"));

        // The whole reason there are two ways to hide: BLANK leaves the gap so
        // nothing around it jumps. Nothing tested the difference before.
        assertThat(painted.isVisible()).isFalse();
        assertThat(painted.isManaged()).as("BLANK is visibility:hidden — the space stays").isTrue();
    }

    @Test
    void aNodeWithNoDisplayStateIsVisible() {
        var painted = (Label) onFxThread(() -> SuiFxRenderer.createDefaultRenderer()
                .mount(UiStack.of(UiText.of("filters", "Filters"))).lookup("#filters"));

        assertThat(painted.isVisible()).isTrue();
        assertThat(painted.isManaged()).isTrue();
    }

    // ── dialogs are windows ───────────────────────────────────────────────

    @Test
    void appendingADialogToTheHostOpensAWindow() {
        // How a server opens a dialog: append it to #sui-dialogs. The SPA has
        // a real container under that id; here there is no such node, so
        // without interception the patch found no target and the button that
        // sent it appeared to do nothing.
        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("body", "page"))));

        var before = onFxThread(() -> Window.getWindows().size());
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.append(
                    SuiFxEventBus.DIALOG_HOST_ID,
                    dialog("test-dialog", "Test", UiText.of("msg", "hello")))));
            return null;
        });

        assertThat(onFxThread(() -> Window.getWindows().size())).isEqualTo(before + 1);
        assertThat(onFxThread(SuiFxChromeTest::stageTitles)).contains("Test");
    }

    @Test
    void removingADialogByIdClosesItsWindow() {
        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("body", "page"))));

        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.append(
                    SuiFxEventBus.DIALOG_HOST_ID,
                    dialog("test-dialog", "Test", UiText.of("msg", "hello")))));
            return null;
        });
        var opened = onFxThread(() -> Window.getWindows().size());

        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.remove("test-dialog")));
            return null;
        });

        assertThat(onFxThread(() -> Window.getWindows().size())).isEqualTo(opened - 1);
    }

    @Test
    void removingADialogThatIsNotOpenIsQuiet() {
        // The server sends REMOVE-then-APPEND to make sure only one is up. The
        // remove names nothing on a first run, and must not be reported as a
        // missing target.
        var bus = new SuiFxEventBus();
        var errors = new AtomicReference<Throwable>();
        bus.setOnError(errors::set);
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("body", "page"))));

        onFxThread(() -> {
            bus.applyPatch(UiPatch.of()
                    .patch(UiPatch.Operation.remove("never-opened"))
                    .patch(UiPatch.Operation.append(SuiFxEventBus.DIALOG_HOST_ID,
                            dialog("d", "D", UiText.of("m", "m")))));
            return null;
        });

        assertThat(errors.get()).isNull();
    }

    @Test
    void aPatchAimedInsideAnOpenDialogStillReachesTheRenderer() {
        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("body", "page"))));

        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.append(
                    SuiFxEventBus.DIALOG_HOST_ID,
                    dialog("d", "D", UiText.of("msg", "before")))));
            return null;
        });

        // The dialog's own content was painted through the main render index,
        // so a node inside it patches like any other.
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(
                    UiPatch.Operation.replace("msg", UiText.of("msg", "after"))));
            return null;
        });

        var msg = onFxThread(() -> bus.context().byId("msg"));
        assertThat(((Label) msg).getText()).isEqualTo("after");
    }

    /** UiDialog.of takes (title, closeHref, node); the id is what a patch names it by. */
    private static UiDialog dialog(String id, String title, ai.mindconnect.ui.model.UiNode body) {
        var d = UiDialog.of(title, null, body);
        d.setId(id);
        return d;
    }

    private static List<String> stageTitles() {
        var titles = new ArrayList<String>();
        for (Window w : Window.getWindows()) {
            if (w instanceof Stage stage && stage.getTitle() != null) titles.add(stage.getTitle());
        }
        return titles;
    }

    private static GridPane findGrid(Node root) {
        if (root instanceof GridPane g && g.getStyleClass().contains("sui-detail-grid")) return g;
        if (root instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                var hit = findGrid(c);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static boolean hasStyleClass(Node root, String styleClass) {
        if (root.getStyleClass().contains(styleClass)) return true;
        if (root instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                if (hasStyleClass(c, styleClass)) return true;
            }
        }
        return false;
    }

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
}
