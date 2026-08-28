package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The {@link UiPage} envelope, over a real socket.
 *
 * <p>A page is how a full navigation comes back — {@code UiTrigger.go(href)} is
 * an {@code APPLY_RESPONSE} GET — so these go through HTTP rather than calling
 * {@link SuiFxEventBus#applyPage} and trusting that the routing in front of it
 * agrees. The routing is the part that was wrong.
 */
class SuiFxPageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;

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
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    /** Serves {@code body} as JSON at {@code path}. */
    private void serve(String path, Object body) {
        server.createContext(path, exchange -> {
            var json = MAPPER.writeValueAsBytes(body);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            try (var out = exchange.getResponseBody()) {
                out.write(json);
            }
        });
    }

    @Test
    void aPageResponseMountsItsNode() {
        var page = new UiPage();
        page.setNode(UiStack.of(UiText.of("greeting", "after")));
        serve("/page", page);

        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("greeting", "before"))));

        go(bus, "/page");

        awaitFx("the page to mount", () -> {
            var label = bus.context().byId("greeting");
            return label instanceof Label text && "after".equals(text.getText());
        });
    }

    @Test
    void aPageCarryingToastsIsNotMistakenForAPatch() {
        // The regression this test exists for: a UiPage has a toasts field, so
        // testing for toasts ahead of the type discriminator read the whole
        // page as a patch and dropped its node.
        var page = new UiPage();
        page.setNode(UiStack.of(UiText.of("greeting", "after")));
        page.setToasts(List.of(UiToast.success("Saved")));
        serve("/page-with-toasts", page);

        var seen = new ArrayList<UiToast>();
        var bus = new SuiFxEventBus();
        bus.setToastHandler(seen::add);
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("greeting", "before"))));

        go(bus, "/page-with-toasts");

        awaitFx("the page to mount", () -> {
            var label = bus.context().byId("greeting");
            return label instanceof Label text && "after".equals(text.getText());
        });
        awaitFx("the toast to arrive", () -> seen.size() == 1);
        assertThat(seen.get(0).getMessage()).isEqualTo("Saved");
    }

    @Test
    void aPatchResponseStillPatchesInPlace() {
        serve("/patch", UiPatch.of().patch(
                UiPatch.Operation.replace("greeting", UiText.of("greeting", "patched"))));

        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("greeting", "before"))));

        go(bus, "/patch");

        awaitFx("the patch to land", () -> {
            var label = bus.context().byId("greeting");
            return label instanceof Label text && "patched".equals(text.getText());
        });
    }

    @Test
    void theNavigateHintGoesToTheHandlerSinceAWindowHasNoAddressBar() {
        var page = new UiPage();
        page.setNode(UiText.of("x", "x"));
        page.setNavigate("/orders/42");

        var seen = new AtomicReference<String>();
        var bus = new SuiFxEventBus();
        bus.setNavigateHandler(seen::set);

        onFxThread(() -> { bus.applyPage(page); return null; });

        awaitFx("the navigate hint", () -> "/orders/42".equals(seen.get()));
    }

    @Test
    void aPageNamingLiveStreamsHandsThemToTheHandler() {
        var page = new UiPage();
        page.setNode(UiText.of("x", "x"));
        page.setActiveStreams(List.of(
                UiPage.ActiveStream.of("chan-1", "/streams/chan-1/resume", "Import", "/imports")));

        var seen = new AtomicReference<List<UiPage.ActiveStream>>();
        var bus = new SuiFxEventBus();
        bus.setActiveStreamHandler(seen::set);

        onFxThread(() -> { bus.applyPage(page); return null; });

        // This bus reads no SSE, so the list is offered rather than acted on.
        awaitFx("the stream list", () -> seen.get() != null);
        assertThat(seen.get()).singleElement()
                .satisfies(s -> assertThat(s.getChannelId()).isEqualTo("chan-1"));
    }

    @Test
    void anEmptyPageIsHarmless() {
        var bus = new SuiFxEventBus();
        var errors = new AtomicReference<Throwable>();
        bus.setOnError(errors::set);

        onFxThread(() -> { bus.applyPage(new UiPage()); return null; });
        onFxThread(() -> { bus.applyPage(null); return null; });

        assertThat(errors.get()).isNull();
    }

    /** Mounts an action pointing at {@code path} and fires it. */
    private void go(SuiFxEventBus bus, String path) {
        onFxThread(() -> {
            var action = UiAction.primary("go", "Go");
            action.setOnClick(UiTrigger.go(baseUrl + path));
            bus.dispatch(action.getOnClick(), action, bus.context());
            return null;
        });
    }

    /**
     * Polls on the FX thread until the condition holds. The response lands over
     * a socket and hops back through {@code Platform.runLater}, so there is no
     * count of hops that reliably covers it.
     */
    private static void awaitFx(String what, BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (onFxThread(condition::getAsBoolean)) return;
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
