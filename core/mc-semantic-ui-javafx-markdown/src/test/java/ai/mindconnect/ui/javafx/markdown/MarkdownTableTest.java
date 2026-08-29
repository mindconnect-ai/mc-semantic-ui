package ai.mindconnect.ui.javafx.markdown;

import ai.mindconnect.ui.ext.markdown.UiMarkdown;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A markdown table has to come out as a table.
 *
 * <p>Tables are a GitHub extension, not CommonMark, and commonmark-java parses
 * only what it is told to. The browser gets them for free — marked has GFM on
 * by default — so a payslip laid out as a table read as a paragraph of pipes
 * on the desktop and as a table everywhere else.
 */
class MarkdownTableTest {

    private static final String PAYSLIP = """
            | Position | Betrag (CHF) |
            |----------|--------------|
            | Monatslohn | 8 615.40 |
            | **Brutto** | **9 608.35** |
            """;

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
    }

    @Test
    void aPipeTableBecomesAGrid() {
        var painted = paint(PAYSLIP);

        var grid = find(painted, GridPane.class);
        assertThat(grid).describedAs("a table, not a paragraph of pipes").isNotNull();
        // Header plus two body rows, two columns.
        assertThat(grid.getChildren()).hasSize(6);
        assertThat(grid.getColumnConstraints()).hasSize(2);
        assertThat(textOf(grid.getChildren().get(0))).isEqualTo("Position");
        assertThat(textOf(grid.getChildren().get(2))).isEqualTo("Monatslohn");
    }

    @Test
    void aHeaderCellReadsAsAHeader() {
        var grid = find(paint(PAYSLIP), GridPane.class);

        assertThat(((TextFlow) grid.getChildren().get(0)).getStyleClass())
                .contains("sui-md-th");
        assertThat(((TextFlow) grid.getChildren().get(0)).getChildren().get(0).getStyleClass())
                .describedAs("bold like a <th>, without the markdown having to say so")
                .contains("sui-md-strong");
    }

    @Test
    void inlineMarkupInsideACellStillWorks() {
        var grid = find(paint(PAYSLIP), GridPane.class);

        // "**Brutto**" is row 2, column 0 — the fifth child added.
        assertThat(((TextFlow) grid.getChildren().get(4)).getChildren().get(0).getStyleClass())
                .contains("sui-md-strong");
    }

    private static Node paint(String markdown) {
        return onFxThread(() -> SuiFxMarkdown.install(SuiFxRenderer.createDefaultRenderer())
                .mount(UiMarkdown.of("doc", markdown)));
    }

    private static String textOf(Node node) {
        var sb = new StringBuilder();
        if (node instanceof TextFlow flow) {
            flow.getChildren().forEach(c -> {
                if (c instanceof Text text) sb.append(text.getText());
            });
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T find(Node root, Class<T> type) {
        if (type.isInstance(root)) return (T) root;
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                var hit = find(child, type);
                if (hit != null) return hit;
            }
        }
        return null;
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
