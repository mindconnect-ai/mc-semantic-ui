package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiRow;
import ai.mindconnect.ui.model.UiTable;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A table the size of what is in it.
 *
 * <p>Two things JavaFX will not work out on its own. A {@code TableView}'s
 * preferred height is a flat 400px whatever it holds, so a table of one
 * attached file came up as a row of data over a lawn of empty grid. And a
 * column has no idea how wide the buttons in it are, so a fixed guess clipped
 * a row action's label to "R…" — the one word on it that mattered.
 */
class SuiFxTableSizeTest {

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

    private static UiTable attachedFiles(boolean withRemove) {
        var table = UiTable.of("files", "Attached Files")
                .column(UiColumn.of("file", "File"))
                .column(UiColumn.of("chunks", "Chunks"));
        var data = new LinkedHashMap<String, Object>();
        data.put("file", "00004189 Lohnabrechnung 01.2026.pdf");
        data.put("chunks", "1");
        table.getRows().add(UiRow.of(data));
        if (withRemove) table.getRowActions().add(UiAction.danger("remove", "Remove"));
        return table;
    }

    @Test
    void aOneRowTableIsOneRowTall() {
        // Shown in a tab, which is where it was noticed: a tab scrolls its
        // panel, so the panel is exactly as tall as it says it is.
        var overlay = new SuiFxOverlay();
        var renderer = SuiFxRenderer.createDefaultRenderer(overlay);
        onFxThread(() -> {
            renderer.mount(ai.mindconnect.ui.model.UiSection.of("tabs", null)
                    .section("files", "Files", attachedFiles(false)));
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(overlay), 900, 700));
            stage.show();
            return null;
        });

        var table = (TableView<?>) onFxThread(
                () -> find(renderer.context().byId("files"), TableView.class));
        // The JavaFX default is a flat 400px: one attached file used to reserve
        // ten rows of nothing under it.
        await("the table to be laid out", () -> onFxThread(() -> table.getHeight() > 0));
        assertThat(onFxThread(table::getHeight))
                .describedAs("as tall as its rows, not as tall as JavaFX guesses")
                .isLessThan(200);
    }

    @Test
    void aRowActionsLabelIsNotClipped() {
        var overlay = new SuiFxOverlay();
        var renderer = SuiFxRenderer.createDefaultRenderer(overlay);
        var bus = new SuiFxEventBus(renderer);
        onFxThread(() -> {
            renderer.mount(attachedFiles(true));
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(overlay), 900, 700));
            stage.show();
            return null;
        });

        // The column widens once the buttons have been laid out, so the
        // assertion has to wait for that rather than read the first guess.
        await("the Remove button to fit its label", () -> onFxThread(() -> {
            var button = find(renderer.context().byId("files"), Button.class);
            return button != null && button.getWidth() >= button.prefWidth(-1) - 0.5;
        }));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T find(Node root, Class<T> type) {
        if (root == null) return null;
        if (type.isInstance(root)) return (T) root;
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                var hit = find(child, type);
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
