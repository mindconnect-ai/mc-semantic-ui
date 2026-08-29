package ai.mindconnect.ui.javafx.iframe;

import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.javafx.SuiFxRenderer;
import ai.mindconnect.ui.model.UiAppShell;
import ai.mindconnect.ui.model.UiHeader;
import ai.mindconnect.ui.model.UiIFrame;
import ai.mindconnect.ui.model.UiMenu;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiText;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The one node type this module adds, rendered through the real renderer.
 *
 * <p>Runs headless via the software pipeline; the toolkit is started once for
 * the whole class, and the tests are skipped rather than failed on a machine
 * with no display.
 */
class SuiFxIFrameTest {

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

    private static SuiFxEventBus bus() {
        return new SuiFxEventBus(SuiFxIFrame.install(SuiFxRenderer.createDefaultRenderer()));
    }

    @Test
    void anIFrameBecomesAWebView() {
        var frame = UiIFrame.of("docs", "about:blank").height("320px");

        var view = (WebView) onFxThread(() -> bus().mount(frame));

        assertThat(view.getStyleClass()).contains("sui-iframe");
        assertThat(view.getPrefHeight()).isEqualTo(320);
    }

    @Test
    void aViewportRelativeHeightLeavesTheFrameToFillItsParent() {
        var frame = UiIFrame.of("docs", "about:blank").height("60vh");

        var view = (WebView) onFxThread(() -> bus().mount(frame));

        // 60vh has no JavaFX equivalent, so the grow hint takes over — the same
        // call ScrollPaneRenderer makes.
        assertThat(javafx.scene.layout.VBox.getVgrow(view))
                .isEqualTo(javafx.scene.layout.Priority.ALWAYS);
    }

    @Test
    void aSandboxWithoutAllowScriptsTurnsJavaScriptOff() {
        var locked = UiIFrame.of("a", "about:blank").sandbox("allow-forms");
        var scripted = UiIFrame.of("b", "about:blank").sandbox("allow-forms allow-scripts");
        var unset = UiIFrame.of("c", "about:blank");

        assertThat(((WebView) onFxThread(() -> bus().mount(locked)))
                .getEngine().isJavaScriptEnabled()).isFalse();
        // The one sandbox distinction a WebView can actually enforce; the rest
        // of the attribute has no counterpart here.
        assertThat(((WebView) onFxThread(() -> bus().mount(scripted)))
                .getEngine().isJavaScriptEnabled()).isTrue();
        assertThat(((WebView) onFxThread(() -> bus().mount(unset)))
                .getEngine().isJavaScriptEnabled()).isTrue();
    }

    @Test
    void heightAcceptsPixelsAndRefusesEverythingElse() {
        assertThat(IFrameRenderer.pixels("320px")).isEqualTo(320);
        assertThat(IFrameRenderer.pixels("320")).isEqualTo(320);
        assertThat(IFrameRenderer.pixels("60vh")).isZero();
        assertThat(IFrameRenderer.pixels("100%")).isZero();
        assertThat(IFrameRenderer.pixels("auto")).isZero();
        assertThat(IFrameRenderer.pixels(null)).isZero();
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
