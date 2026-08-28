package ai.mindconnect.ui.javafx.markdown;

import ai.mindconnect.ui.ext.markdown.UiMarkdown;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
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
 * Markdown as a scene graph rather than a page in an embedded browser.
 */
class MarkdownRendererTest {

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

    private static VBox paint(String markdown) {
        return (VBox) onFxThread(() -> SuiFxMarkdown.install(SuiFxRenderer.createDefaultRenderer())
                .mount(UiMarkdown.of("doc", markdown)));
    }

    @Test
    void headingsCarryTheirLevel() {
        var painted = paint("# Title\n\n## Section");

        var headings = nodes(painted, Label.class).stream()
                .filter(l -> l.getStyleClass().contains("sui-md-heading")).toList();
        assertThat(headings).hasSize(2);
        assertThat(headings.get(0).getText()).isEqualTo("Title");
        assertThat(headings.get(0).getStyleClass()).contains("sui-md-h1");
        assertThat(headings.get(1).getStyleClass()).contains("sui-md-h2");
    }

    @Test
    void emphasisAccumulatesThroughNesting() {
        var painted = paint("plain **bold** and *italic* and ***both***");

        var runs = nodes(painted, Text.class);
        assertThat(runs).anyMatch(t -> "bold".equals(t.getText())
                && t.getStyleClass().contains("sui-md-strong"));
        assertThat(runs).anyMatch(t -> "italic".equals(t.getText())
                && t.getStyleClass().contains("sui-md-emphasis"));
        // Nested emphasis arrives carrying both, which is why the styles are
        // collected down the tree rather than set per node.
        assertThat(runs).anyMatch(t -> "both".equals(t.getText())
                && t.getStyleClass().contains("sui-md-strong")
                && t.getStyleClass().contains("sui-md-emphasis"));
    }

    @Test
    void inlineCodeAndCodeBlocksAreToldApart() {
        var painted = paint("Use `flag` here.\n\n```\nline one\nline two\n```");

        assertThat(nodes(painted, Text.class))
                .anyMatch(t -> "flag".equals(t.getText()) && t.getStyleClass().contains("sui-md-code"));
        var block = nodes(painted, Label.class).stream()
                .filter(l -> l.getStyleClass().contains("sui-md-code-block")).findFirst();
        assertThat(block).isPresent();
        assertThat(block.get().getText()).isEqualTo("line one\nline two");
        // Code does not reflow — wrapping would break the alignment that makes
        // it readable in the first place.
        assertThat(block.get().isWrapText()).isFalse();
    }

    @Test
    void aSoftLineBreakIsASpaceNotABreak() {
        var painted = paint("one\ntwo");

        var joined = nodes(painted, Text.class).stream().map(Text::getText).reduce("", String::concat);
        assertThat(joined).isEqualTo("one two");
    }

    @Test
    void listsGetTheirMarkers() {
        var bullets = paint("- alpha\n- beta");
        var numbers = paint("3. third\n4. fourth");

        assertThat(nodes(bullets, Label.class)).anyMatch(l -> "•  ".equals(l.getText()));
        // An ordered list starting at 3 keeps counting from 3.
        assertThat(nodes(numbers, Label.class)).anyMatch(l -> "3.  ".equals(l.getText()));
        assertThat(nodes(numbers, Label.class)).anyMatch(l -> "4.  ".equals(l.getText()));
    }

    @Test
    void aLinkBecomesAClickableHyperlink() {
        var painted = paint("see [the docs](/admin/docs) for more");

        var link = nodes(painted, Hyperlink.class);
        assertThat(link).hasSize(1);
        assertThat(link.get(0).getText()).isEqualTo("the docs");
        // Wired, so a relative href goes through the bus and resolves against
        // the page the document arrived on.
        assertThat(link.get(0).getOnAction()).isNotNull();
    }

    @Test
    void anImageShowsItsAltTextRatherThanLoading() {
        var painted = paint("![a diagram](/img/x.png)");

        // A document's images are the one thing that can hang a window on a
        // slow link, so the alt text stands in for them.
        assertThat(nodes(painted, Text.class)).anyMatch(t -> "[a diagram]".equals(t.getText()));
    }

    @Test
    void quotesAndRulesPaintAsThemselves() {
        var quoted = paint("> quoted");
        var ruled = paint("text\n\n---\n\nmore");

        assertThat(nodes(quoted, VBox.class)).anyMatch(v -> v.getStyleClass().contains("sui-md-quote"));
        assertThat(nodes(ruled, Separator.class)).hasSize(1);
    }

    @Test
    void emptyContentPaintsAnEmptyBlockRatherThanFailing() {
        assertThat(paint("").getChildren()).isEmpty();
        assertThat(paint(null).getChildren()).isEmpty();
    }

    @Test
    void aParagraphIsOneFlowSoItWraps() {
        var painted = paint("a long paragraph of prose");

        // One TextFlow per paragraph: the runs have to share a line box or
        // every word would wrap on its own.
        assertThat(nodes(painted, TextFlow.class)).hasSize(1);
    }

    private static <T> List<T> nodes(Node root, Class<T> type) {
        var out = new ArrayList<T>();
        collect(root, type, out);
        return out;
    }

    private static <T> void collect(Node n, Class<T> type, List<T> out) {
        if (type.isInstance(n)) out.add(type.cast(n));
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) collect(c, type, out);
        }
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
