package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What the user was doing has to survive a repaint.
 *
 * <p>The browser gets this from Idiomorph: a patch is morphed into the DOM and
 * whatever did not change is never touched. JavaFX rebuilds and swaps, so
 * these are the pieces that would otherwise be thrown away with the old
 * controls — typing in a field while a stream patched the page above it used
 * to cost the cursor mid-word.
 */
class FxViewStateTest {

    private Stage stage;

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
        Platform.setImplicitExit(false);
    }

    @AfterEach
    void closeStage() {
        if (stage != null) onFxThread(() -> { stage.close(); return null; });
    }

    /** A shown window, because focus is not a thing a detached node can have. */
    private SuiFxEventBus shown(ai.mindconnect.ui.model.UiNode tree) {
        var overlay = new SuiFxOverlay();
        var renderer = SuiFxRenderer.createDefaultRenderer(overlay);
        var bus = new SuiFxEventBus(renderer);
        onFxThread(() -> {
            renderer.mount(tree);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(overlay), 800, 600));
            stage.show();
            return null;
        });
        return bus;
    }

    @Test
    void aCaretSurvivesARepaintOfItsOwnSubtree() {
        var form = UiForm.of("form", null)
                .field(UiField.text("note", "Note", "hello world").asEditable());
        var bus = shown(UiStack.of(UiText.of("banner", "before"), form));

        var input = (TextInputControl) onFxThread(() -> firstInput(bus.context().byId("note")));
        onFxThread(() -> {
            input.requestFocus();
            input.positionCaret(5);
            return null;
        });
        await("focus to land", () -> onFxThread(() -> hasFocus(input)));

        // A patch repaints the form the user is typing in.
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.merge(
                    "form", Map.of("title", "Notes"))));
            return null;
        });

        var repainted = (TextInputControl) onFxThread(() -> firstInput(bus.context().byId("note")));
        await("focus to come back", () -> onFxThread(() -> hasFocus(repainted)));
        assertThat(onFxThread(repainted::getCaretPosition)).isEqualTo(5);
    }

    @Test
    void aSelectionSurvivesTooNotJustTheCursor() {
        var form = UiForm.of("form", null)
                .field(UiField.text("note", "Note", "hello world").asEditable());
        var bus = shown(UiStack.of(form));

        var input = (TextInputControl) onFxThread(() -> firstInput(bus.context().byId("note")));
        onFxThread(() -> {
            input.requestFocus();
            input.selectRange(0, 5);
            return null;
        });
        await("the selection", () -> onFxThread(() -> "hello".equals(input.getSelectedText())));

        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.merge("form", Map.of("title", "Notes"))));
            return null;
        });

        var repainted = (TextInputControl) onFxThread(() -> firstInput(bus.context().byId("note")));
        // A bare cursor is a selection of nothing, so restoring one restores
        // the other — no reason to handle them separately.
        await("the selection to come back",
                () -> onFxThread(() -> "hello".equals(repainted.getSelectedText())));
    }

    @Test
    void theOpenTabStaysOpen() {
        var tabs = UiSection.of("tabs", null)
                .section("one", "First", UiText.of("a", "a"))
                .section("two", "Second", UiText.of("b", "b"));
        var bus = shown(UiStack.of(tabs));

        onFxThread(() -> {
            ((TabPane) bus.context().byId("tabs")).getSelectionModel().select(1);
            return null;
        });

        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.merge("tabs", Map.of("title", "Sections"))));
            return null;
        });

        // Being sent back to the first tab by an unrelated patch is the kind of
        // thing that makes an app feel broken.
        var repainted = (TabPane) onFxThread(() -> bus.context().byId("tabs"));
        assertThat(onFxThread(() -> repainted.getSelectionModel().getSelectedIndex())).isEqualTo(1);
    }

    @Test
    void anUnrelatedRepaintLeavesFocusWhereItWas() {
        var form = UiForm.of("form", null)
                .field(UiField.text("note", "Note", "typing").asEditable());
        var bus = shown(UiStack.of(UiText.of("banner", "before"), form));

        var input = (TextInputControl) onFxThread(() -> firstInput(bus.context().byId("note")));
        onFxThread(() -> { input.requestFocus(); return null; });
        await("focus", () -> onFxThread(() -> hasFocus(input)));

        // A patch somewhere else entirely — the streaming case.
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(
                    UiPatch.Operation.replace("banner", UiText.of("banner", "after"))));
            return null;
        });

        // Nothing touched the form, so the very same control still has focus.
        assertThat(onFxThread(() -> hasFocus(input))).isTrue();
    }

    @Test
    void aScrollPositionSurvivesARepaintInsideIt() {
        var lines = new ai.mindconnect.ui.model.UiStack();
        for (int i = 0; i < 60; i++) {
            lines.getChildren().add(UiText.of("line-" + i, "line " + i));
        }
        var scroller = ai.mindconnect.ui.model.UiScrollPane.of("log", lines).maxHeight("200px");
        var bus = shown(UiStack.of(scroller));

        var pane = (javafx.scene.control.ScrollPane) onFxThread(() -> bus.context().byId("log"));
        onFxThread(() -> { pane.setVvalue(0.7); return null; });
        await("the scroll to take", () -> onFxThread(() -> pane.getVvalue() > 0.5));

        // A line in the middle of the log is repainted, as a stream would.
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(
                    UiPatch.Operation.merge("line-30", Map.of("text", "line 30 (updated)"))));
            return null;
        });

        // Being thrown back to the top because something above changed is the
        // single most annoying thing a live page can do.
        var repainted = (javafx.scene.control.ScrollPane) onFxThread(() -> bus.context().byId("log"));
        await("the scroll to be put back", () -> onFxThread(() -> repainted.getVvalue() > 0.5));
    }

    /**
     * The scene's focus owner, not Node#isFocused — the latter is also false
     * when the window is inactive, which it may well be while a suite runs.
     */
    private static boolean hasFocus(Node node) {
        var scene = node.getScene();
        return scene != null && scene.getFocusOwner() == node;
    }

    private static TextInputControl firstInput(Node root) {
        if (root instanceof TextInputControl input) return input;
        if (root instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                var hit = firstInput(c);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static void await(String what, BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        } while (System.nanoTime() < deadline);
        throw new AssertionError("timed out waiting for " + what);
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
