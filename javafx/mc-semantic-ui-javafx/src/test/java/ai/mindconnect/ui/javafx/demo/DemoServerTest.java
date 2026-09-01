package ai.mindconnect.ui.javafx.demo;

import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.model.UiUpload;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end over a real socket: the demo server answers with a
 * {@link ai.mindconnect.ui.model.UiPatch}, and the JavaFX client applies it
 * without knowing anything about the endpoint.
 *
 * <p>That claim — "the server sends UI, the client just renders it" — is the
 * premise of the whole repo, so it deserves a test that actually goes over
 * HTTP rather than one that trusts the serialisation by inspection.
 *
 * <p>No test here touches the internet; the weather call in the demo is
 * deliberately not covered, because a unit test that depends on a public API
 * being up is a test that fails for reasons unrelated to this code.
 */
class DemoServerTest {

    private DemoServer server;

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

    @BeforeEach
    void startServer() throws Exception {
        server = new DemoServer();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void theInventoryEndpointAnswersWithUiTheClientApplies() throws Exception {
        var bus = new SuiFxEventBus();
        var toasts = new AtomicReference<UiToast>();
        var panel = UiStack.of(UiText.of("Not loaded yet."));
        panel.setId("inventory-panel");

        onFxThread(() -> {
            bus.setToastHandler(toasts::set);
            bus.mount(UiStack.of(panel));
            // Exactly what the demo's button does — and note there is no
            // handler registered for this url anywhere.
            bus.dispatch(UiTrigger.api("GET", server.url("/api/inventory")), panel, bus.context());
            return null;
        });

        // Search the whole subtree: the renderer wraps a UiTable in a VBox of
        // [title, TableView], so the table sits below the patched panel rather
        // than directly inside it.
        var view = await(() -> {
            var node = bus.context().byId("inventory-panel");
            return node == null ? null : firstOfType(node, TableView.class);
        });

        assertThat(view).as("the server's table should have been painted into the panel").isNotNull();
        // The rows came off the wire as JSON and were painted by the normal
        // table renderer.
        assertThat(view.getItems()).hasSize(3);
        assertThat(view.getColumns()).hasSize(3);
        assertThat(toasts.get()).isNotNull();
        assertThat(toasts.get().getMessage()).contains("3 items");
    }

    @Test
    void theUploadEndpointReceivesTheFileAndAnswersWithUi() throws Exception {
        var file = Files.createTempFile("sui-demo-", ".txt");
        Files.writeString(file, "payload");

        var bus = new SuiFxEventBus();
        var result = UiStack.of(UiText.of("Nothing uploaded yet."));
        result.setId("upload-result");
        var upload = UiUpload.of("server-upload", "Upload").uploadTo(server.url("/api/files"));

        onFxThread(() -> {
            // Without a handler the bus falls back to a real Alert window —
            // which pops up during `mvn test` and leaves a stray dialog behind.
            bus.setToastHandler(toast -> { });
            bus.mount(UiStack.of(result));
            bus.dispatch(upload.getOnUpload(), upload, bus.context(), List.of(file.toFile()));
            return null;
        });

        var text = await(() -> {
            var node = bus.context().byId("upload-result");
            if (!(node instanceof VBox box) || box.getChildren().isEmpty()) return null;
            var label = box.getChildren().get(0);
            return label instanceof javafx.scene.control.Label l
                    && l.getText().contains(file.getFileName().toString())
                    ? l.getText()
                    : null;
        });

        // The server saw the real filename, so the multipart body arrived in a
        // shape a server can actually parse.
        assertThat(text).contains(file.getFileName().toString());
        Files.deleteIfExists(file);
    }

    // ── harness ───────────────────────────────────────────────────────────

    /**
     * Depth-first search for the first node of a type. Asserting on the exact
     * nesting would make these tests break whenever a renderer gains a wrapper,
     * which says nothing about whether the server's UI arrived.
     */
    private static <T extends Node> T firstOfType(Node root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                var found = firstOfType(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Polls on the FX thread until {@code check} returns non-null, or gives up. */
    private <T> T await(Supplier<T> check) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            var value = onFxThread(check::get);
            if (value != null) return value;
            Thread.sleep(50);
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
