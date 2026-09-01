package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiScrollPane;
import ai.mindconnect.ui.model.UiSection;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A patch has to reach the slot the node actually sits in.
 *
 * <p>Half the vocabulary paints as a JavaFX <em>control</em> rather than a
 * pane: a tab's panel, a scroll pane's content, a collapsible's body. Those
 * hold their child in a property, and the skin puts it inside a private
 * container of its own. Walking to {@code getParent()} lands in that private
 * container, so swapping the child there leaves the control still pointing at
 * the node that was taken out — and the newcomer misses everything the
 * control does for its content: a {@code ScrollPane} stretches only the node
 * its {@code content} property names.
 *
 * <p>Which is why the symptom was a page that collapsed to a column of
 * ellipses the moment anything on it was deleted.
 */
class SuiFxPatchSlotTest {

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
    void aTabsPanelStillFillsItsWidthAfterAPatch() {
        var table = UiTable.of("sessions", "Sessions").column(UiColumn.of("title", "Title"));
        var bus = shown(UiSection.of("tabs", null).section("s", "Sessions", table));

        // What a delete does: the server hands back the same table, one row short.
        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace("sessions",
                    UiTable.of("sessions", "Sessions").column(UiColumn.of("title", "Title")))));
            return null;
        });

        var scroll = (ScrollPane) onFxThread(() -> scrollIn(bus.context().byId("tabs")));
        var repainted = (Node) onFxThread(() -> bus.context().byId("sessions"));
        assertThat(onFxThread(scroll::getContent))
                .describedAs("the panel the tab scrolls is the one that is on screen")
                .isSameAs(repainted);
        // fitToWidth only ever stretches the node the content property names.
        await("the panel to be stretched again",
                () -> onFxThread(() -> repainted.getLayoutBounds().getWidth() > 400));
    }

    @Test
    void aScrollPanesOwnContentIsSwappedOnThePane() {
        var lines = UiStack.of(UiText.of("line", "one"));
        lines.setId("lines");
        var bus = shown(UiStack.of(UiScrollPane.of("log", lines)));

        onFxThread(() -> {
            var replacement = UiStack.of(UiText.of("line", "two"));
            replacement.setId("lines");
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace("lines", replacement)));
            return null;
        });

        var scroll = (ScrollPane) onFxThread(() -> bus.context().byId("log"));
        var repainted = (Node) onFxThread(() -> bus.context().byId("lines"));
        assertThat(onFxThread(scroll::getContent))
                .describedAs("the pane scrolls what the patch put in it")
                .isSameAs(repainted);
        await("the content to be stretched again",
                () -> onFxThread(() -> repainted.getLayoutBounds().getWidth() > 400));
    }

    @Test
    void aChildOfAnOrdinaryPaneIsStillPatchedInPlace() {
        var bus = shown(UiStack.of(UiText.of("a", "one"), UiText.of("b", "two")));

        onFxThread(() -> {
            bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace(
                    "a", UiText.of("a", "changed"))));
            return null;
        });

        // The common case has to keep its position among its siblings.
        var parent = (javafx.scene.layout.Pane) onFxThread(
                () -> (javafx.scene.layout.Pane) bus.context().byId("a").getParent());
        assertThat(onFxThread(() -> parent.getChildren().indexOf(bus.context().byId("a"))))
                .isEqualTo(0);
    }

    private static ScrollPane scrollIn(Node tabPane) {
        var tabs = (TabPane) tabPane;
        return (ScrollPane) tabs.getTabs().get(0).getContent();
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
