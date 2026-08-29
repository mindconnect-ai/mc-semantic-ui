package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiText;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A dialog has to look like the app it came out of.
 *
 * <p>A dialog is a window, and a window is its own scene — a scene that starts
 * from nothing but the JavaFX default theme. Copying the owner <em>scene's</em>
 * stylesheets is not enough on its own: {@link SuiFxOverlay} loads
 * {@code sui-fx.css} onto itself, and {@link SuiFxStyles#install} exists so an
 * app can do the same on a root of its own. In both cases the scene's own list
 * is empty and there is nothing to inherit, which is how a dialog full of tabs
 * and buttons came up looking like a different application.
 */
class SuiFxDialogStyleTest {

    private Stage stage;
    private Stage dialog;

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
    void closeWindows() {
        onFxThread(() -> {
            if (dialog != null) dialog.close();
            if (stage != null) stage.close();
            return null;
        });
    }

    @Test
    void aDialogPicksUpTheStylesTheAppPutOnItsRoot() {
        var overlay = new SuiFxOverlay();
        var renderer = SuiFxRenderer.createDefaultRenderer(overlay);
        var bus = new SuiFxEventBus(renderer);

        var appCss = "data:text/css," + "%2E" + "app%7B%7D";   // a sheet of the app's own
        onFxThread(() -> {
            // Exactly what a host app does — and what the browser client does.
            renderer.mount(UiText.of("page", "page"));
            var root = new BorderPane(overlay);
            SuiFxStyles.install(root);
            root.getStylesheets().add(appCss);
            stage = new Stage();
            stage.setScene(new Scene(root, 800, 600));
            stage.show();
            return null;
        });

        dialog = onFxThread(() -> bus.showDialog(
                UiDialog.of("Workspace", null, UiText.of("t", "hello"))));

        var sheets = onFxThread(() -> dialog.getScene().getStylesheets());
        assertThat(sheets)
                .describedAs("the palette, so tabs and buttons are not raw JavaFX")
                .anyMatch(s -> s.endsWith(SuiFxStyles.STYLESHEET));
        assertThat(sheets)
                .describedAs("and whatever the app styled its own root with")
                .contains(appCss);
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
