package ai.mindconnect.ui.javafx.browser;

import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The browser against a server that speaks nothing but {@code UiNode} JSON.
 *
 * <p>That is the experiment this module exists for: point it at any endpoint
 * the SPA renderer can drive, and see whether the same JSON comes up the same
 * way on the desktop.
 */
class SuiBrowserTest {

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

    private void servePage(String path, UiPage page) {
        server.createContext(path, exchange -> {
            var json = MAPPER.writeValueAsBytes(page);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            try (var out = exchange.getResponseBody()) {
                out.write(json);
            }
        });
    }

    private static UiPage pageSaying(String text) {
        var page = new UiPage();
        page.setNode(UiStack.of(UiText.of("body", text)));
        return page;
    }

    @Test
    void typingAUrlRendersWhateverTheServerSends() {
        servePage("/ui", pageSaying("hello from the server"));

        var browser = onFxThread(SuiBrowser::new);
        onFxThread(() -> { browser.go(baseUrl + "/ui"); return null; });

        awaitFx("the page", () -> textIs(browser, "hello from the server"));
    }

    @Test
    void aPagesNavigateHintUpdatesTheAddressBar() {
        // The desktop's answer to a history push: the bus has no address bar of
        // its own, so the browser lends it this one.
        var page = pageSaying("moved");
        page.setNavigate("/ui/orders/42");
        servePage("/ui", page);

        var browser = onFxThread(SuiBrowser::new);
        onFxThread(() -> { browser.go(baseUrl + "/ui"); return null; });

        awaitFx("the address bar to follow", () ->
                "/ui/orders/42".equals(browser.history().current()));
    }

    @Test
    void historyWalksBackAndForward() {
        servePage("/one", pageSaying("one"));
        servePage("/two", pageSaying("two"));

        var browser = onFxThread(SuiBrowser::new);
        var history = browser.history();

        onFxThread(() -> { browser.go(baseUrl + "/one"); return null; });
        awaitFx("the first page", () -> textIs(browser, "one"));
        onFxThread(() -> { browser.go(baseUrl + "/two"); return null; });
        awaitFx("the second page", () -> textIs(browser, "two"));

        assertThat(history.canGoBackProperty().get()).isTrue();
        assertThat(history.canGoForwardProperty().get()).isFalse();

        assertThat(history.back()).isEqualTo(baseUrl + "/one");
        assertThat(history.canGoForwardProperty().get()).isTrue();
        assertThat(history.forward()).isEqualTo(baseUrl + "/two");
    }

    @Test
    void aNewTurnDropsTheForwardBranch() {
        var history = new BrowserHistory();
        history.visit("/a");
        history.visit("/b");
        history.back();

        // Sitting on /a with /b ahead; going somewhere else must not leave /b
        // reachable, or forward would lead to a branch the user abandoned.
        history.visit("/c");

        assertThat(history.current()).isEqualTo("/c");
        assertThat(history.canGoForwardProperty().get()).isFalse();
        assertThat(history.back()).isEqualTo("/a");
    }

    @Test
    void revisitingTheCurrentUrlIsNotANewEntry() {
        var history = new BrowserHistory();
        history.visit("/a");
        history.visit("/a");

        // Otherwise reload would pile up entries and back would go nowhere.
        assertThat(history.canGoBackProperty().get()).isFalse();
    }

    @Test
    void anAddressWithoutASchemeStillWorks() {
        // Typing "localhost:8080/ui" and being told off about a missing scheme
        // is the kind of thing that makes a tool feel hostile.
        assertThat(SuiBrowser.normalize("localhost:8080/ui")).isEqualTo("http://localhost:8080/ui");
        assertThat(SuiBrowser.normalize("  example.com  ")).isEqualTo("http://example.com");
        assertThat(SuiBrowser.normalize("https://example.com")).isEqualTo("https://example.com");
        assertThat(SuiBrowser.normalize("")).isNull();
        assertThat(SuiBrowser.normalize(null)).isNull();
    }

    @Test
    void theStartUrlComesFromTheArgumentsThenTheProperty() {
        assertThat(SuiBrowserApp.startUrl(List.of("http://a"))).isEqualTo("http://a");
        assertThat(SuiBrowserApp.startUrl(List.of())).isNull();

        System.setProperty(SuiBrowserApp.START_URL_PROPERTY, "http://from-property");
        try {
            assertThat(SuiBrowserApp.startUrl(List.of())).isEqualTo("http://from-property");
            assertThat(SuiBrowserApp.startUrl(List.of("http://from-args"))).isEqualTo("http://from-args");
        } finally {
            System.clearProperty(SuiBrowserApp.START_URL_PROPERTY);
        }
    }

    /**
     * Null-safe on purpose: until the first response lands there is no mounted
     * tree and so no context, and a poll that ran into that would fail the test
     * instead of waiting for the page it is waiting for.
     */
    private static boolean textIs(SuiBrowser browser, String expected) {
        var ctx = browser.renderer().context();
        if (ctx == null) return false;
        return ctx.byId("body") instanceof Label label && expected.equals(label.getText());
    }

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
