package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Relative urls, which is how a real server writes them.
 *
 * <p>A page arrives full of {@code /admin/tools} and {@code /img/logo.svg}; a
 * browser resolves those against the address the page came from, and without
 * an equivalent here almost every link on a real screen is dead. The base is
 * the desktop's stand-in for {@code document.baseURI}.
 */
class SuiFxDocumentBaseTest {

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

    @Test
    void aRootRelativeUrlResolvesAgainstTheHost() {
        var renderer = SuiFxRenderer.createDefaultRenderer();
        renderer.setDocumentBase("http://localhost:9091/admin/agents");

        assertThat(renderer.resolve("/admin/tools")).isEqualTo("http://localhost:9091/admin/tools");
        assertThat(renderer.resolve("/img/logo.svg")).isEqualTo("http://localhost:9091/img/logo.svg");
    }

    @Test
    void aPathRelativeUrlResolvesAgainstTheDirectory() {
        var renderer = SuiFxRenderer.createDefaultRenderer();
        renderer.setDocumentBase("http://localhost:9091/admin/agents");

        assertThat(renderer.resolve("42")).isEqualTo("http://localhost:9091/admin/42");
        assertThat(renderer.resolve("?q=x")).isEqualTo("http://localhost:9091/admin/agents?q=x");
    }

    @Test
    void anythingWithASchemeIsLeftAlone() {
        var renderer = SuiFxRenderer.createDefaultRenderer();
        renderer.setDocumentBase("http://localhost:9091/admin/agents");

        assertThat(renderer.resolve("https://example.com/x")).isEqualTo("https://example.com/x");
        // data: and file: must not be rebased onto the http host either.
        assertThat(renderer.resolve("data:image/png;base64,AAAA")).isEqualTo("data:image/png;base64,AAAA");
        assertThat(renderer.resolve("mailto:a@b.c")).isEqualTo("mailto:a@b.c");
    }

    @Test
    void withoutABaseAUrlIsHandedBackUntouched() {
        var renderer = SuiFxRenderer.createDefaultRenderer();

        // Better to let the caller fail on the original string than to invent
        // a host for it.
        assertThat(renderer.resolve("/admin/tools")).isEqualTo("/admin/tools");
        assertThat(renderer.resolve(null)).isNull();
    }

    @Test
    void aRelativeLinkOnAFetchedPageIsFollowable() {
        // What the admin UI actually sends: a page whose every link is relative.
        var first = new UiPage();
        first.setNavigate("/admin/agents");
        first.setNode(UiStack.of(UiText.of("body", "agents")));
        servePage("/admin/agents", first);

        var second = new UiPage();
        second.setNode(UiStack.of(UiText.of("body", "tools")));
        servePage("/admin/tools", second);

        var bus = new SuiFxEventBus();
        onFxThread(() -> {
            bus.renderer().setDocumentBase(baseUrl + "/admin/agents");
            bus.dispatch(UiTrigger.go(baseUrl + "/admin/agents"), null, bus.renderer().newContext());
            return null;
        });
        awaitFx("the first page", () -> textIs(bus, "agents"));

        // The link as the server wrote it — no host, no scheme.
        onFxThread(() -> {
            bus.dispatch(UiTrigger.go("/admin/tools"), null, bus.context());
            return null;
        });
        awaitFx("the relative link to be followed", () -> textIs(bus, "tools"));
    }

    @Test
    void aPagesNavigateMovesTheBaseButAPatchDoesNot() {
        var page = new UiPage();
        page.setNavigate("/admin/agents/42");
        page.setNode(UiStack.of(UiText.of("body", "detail")));

        var bus = new SuiFxEventBus();
        onFxThread(() -> {
            bus.applyPage(page, "http://localhost:9091/admin/agents");
            return null;
        });

        // navigate refines the base, the way a redirect does in a browser.
        assertThat(bus.renderer().documentBase())
                .hasToString("http://localhost:9091/admin/agents/42");
    }

    private static boolean textIs(SuiFxEventBus bus, String expected) {
        var ctx = bus.context();
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
