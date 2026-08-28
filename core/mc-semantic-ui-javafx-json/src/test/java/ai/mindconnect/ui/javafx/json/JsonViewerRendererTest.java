package ai.mindconnect.ui.javafx.json;

import ai.mindconnect.ui.ext.jsonviewer.UiJsonViewer;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A payload, indented, in something you can select and copy out of.
 */
class JsonViewerRendererTest {

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

    private static TextArea paint(String json) {
        return (TextArea) onFxThread(() -> SuiFxJson.install(SuiFxRenderer.createDefaultRenderer())
                .mount(UiJsonViewer.of("payload", json)));
    }

    @Test
    void aPayloadIsIndented() {
        var area = paint("{\"a\":1,\"b\":{\"c\":true}}");

        assertThat(area.getText()).contains("\n");
        assertThat(area.getText().lines().count()).isGreaterThan(1);
        assertThat(area.getText()).contains("\"c\" : true");
    }

    @Test
    void itCanBeSelectedButNotEdited() {
        // The reason a payload is on screen is usually that someone wants a
        // value out of it.
        var area = paint("{\"a\":1}");

        assertThat(area.isEditable()).isFalse();
        // JSON is read by its indentation, so it must not reflow.
        assertThat(area.isWrapText()).isFalse();
    }

    @Test
    void malformedInputIsShownAsItCame() {
        // Broken JSON is exactly what someone is looking at the payload to
        // find; replacing it with an error message would hide the evidence.
        var broken = "{\"a\": ";

        assertThat(JsonViewerRenderer.format(broken)).isEqualTo(broken);
        assertThat(paint(broken).getText()).isEqualTo(broken);
    }

    @Test
    void anEmptyPayloadIsEmptyRatherThanAFailure() {
        assertThat(JsonViewerRenderer.format(null)).isEmpty();
        assertThat(JsonViewerRenderer.format("   ")).isEmpty();
        assertThat(paint(null).getText()).isEmpty();
    }

    @Test
    void aHugePayloadScrollsRatherThanGrowingWithoutEnd() {
        var big = new StringBuilder("{");
        for (int i = 0; i < 200; i++) {
            big.append(i > 0 ? "," : "").append("\"k").append(i).append("\":").append(i);
        }
        var area = paint(big.append("}").toString());

        assertThat(area.getText().lines().count()).isGreaterThan(JsonViewerRenderer.MAX_VISIBLE_ROWS);
        assertThat(area.getPrefRowCount()).isEqualTo(JsonViewerRenderer.MAX_VISIBLE_ROWS);
    }

    @Test
    void aSmallPayloadIsNotPaddedOutToTheCap() {
        var area = paint("{\"a\":1}");

        assertThat(area.getPrefRowCount()).isLessThan(JsonViewerRenderer.MAX_VISIBLE_ROWS);
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
