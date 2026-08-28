package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiTrigger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
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
 * The {@code STREAM} behaviour against a real SSE endpoint.
 *
 * <p>An agent run is the case this exists for: the server writes patches as it
 * thinks, and the window has to paint each one as it lands rather than waiting
 * for the whole answer.
 */
class SuiFxStreamTest {

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

    /** Serves an SSE stream that writes {@code blocks} and then closes. */
    private void serveSse(String path, List<String> headers, String... blocks) {
        server.createContext(path, exchange -> {
            for (int i = 0; i + 1 < headers.size(); i += 2) {
                exchange.getResponseHeaders().add(headers.get(i), headers.get(i + 1));
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String block : blocks) {
                    out.write(block.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        });
    }

    private static String patchEvent(long seq, String id, String text) {
        return "id: " + seq + "\nevent: patch\n"
                + "data: {\"patches\":[{\"op\":\"REPLACE\",\"targetId\":\"" + id + "\","
                + "\"node\":{\"type\":\"text\",\"id\":\"" + id + "\",\"text\":\"" + text + "\"}}]}\n\n";
    }

    @Test
    void everyPatchEventLandsAsItArrives() {
        serveSse("/agent", List.of("Sui-Stream-Channel", "run-7", "Sui-Stream-Label", "Import"),
                patchEvent(1, "answer", "thinking"),
                patchEvent(2, "answer", "almost"),
                patchEvent(3, "answer", "done"));

        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("answer", "idle"))));

        stream(bus, "/agent");

        awaitFx("the last patch to land", () -> {
            var node = bus.context().byId("answer");
            return node instanceof Label text && "done".equals(text.getText());
        });
        // The server publishes its monotonic seq as the SSE id; tracking it is
        // what lets a reconnect skip what already arrived.
        var handle = bus.activeStreams().iterator().next();
        assertThat(handle.channelId()).isEqualTo("run-7");
        assertThat(handle.label()).isEqualTo("Import");
        awaitFx("the stream to finish", () -> handle.lastSeq() == 3
                && handle.state() == FxStreamHandle.State.COMPLETED);
    }

    @Test
    void aChannellessServerStillGetsAHandle() {
        serveSse("/anon", List.of(), patchEvent(1, "answer", "hi"));

        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("answer", "idle"))));

        stream(bus, "/anon");

        awaitFx("a handle to appear", () -> !bus.activeStreams().isEmpty());
        // No Sui-Stream-Channel header, so the bus makes an id up rather than
        // refusing to track the stream.
        assertThat(bus.activeStreams().iterator().next().channelId()).startsWith("sse-");
    }

    @Test
    void appsMayRegisterTheirOwnEventNames() {
        serveSse("/agent", List.of(),
                "event: token\ndata: hel\n\n",
                "event: token\ndata: lo\n\n",
                "event: done\ndata: {}\n\n");

        var tokens = new StringBuilder();
        var finished = new CountDownLatch(1);
        var bus = new SuiFxEventBus();
        bus.onStreamEvent("token", (data, handle) -> tokens.append(data));
        bus.onStreamEvent("done", (data, handle) -> finished.countDown());

        stream(bus, "/agent");

        awaitFx("the done event", () -> finished.getCount() == 0);
        assertThat(tokens.toString()).isEqualTo("hello");
    }

    @Test
    void multiLineDataArrivesAsOnePayload() {
        serveSse("/agent", List.of(), "event: note\ndata: first\ndata: second\n\n");

        var seen = new AtomicReference<String>();
        var bus = new SuiFxEventBus();
        bus.onStreamEvent("note", (data, handle) -> seen.set(data));

        stream(bus, "/agent");

        awaitFx("the note", () -> seen.get() != null);
        assertThat(seen.get()).isEqualTo("first\nsecond");
    }

    @Test
    void keepAliveCommentsAndUnknownEventsAreIgnored() {
        serveSse("/agent", List.of(),
                ": keep-alive\n\n",
                "event: nobody-listens\ndata: {}\n\n",
                patchEvent(1, "answer", "through"));

        var bus = new SuiFxEventBus();
        var errors = new AtomicReference<Throwable>();
        bus.setOnError(errors::set);
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("answer", "idle"))));

        stream(bus, "/agent");

        awaitFx("the patch after the noise", () -> {
            var node = bus.context().byId("answer");
            return node instanceof Label text && "through".equals(text.getText());
        });
        assertThat(errors.get()).isNull();
    }

    @Test
    void anHttpErrorIsReportedRatherThanReadAsAStream() {
        server.createContext("/boom", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        var bus = new SuiFxEventBus();
        var errors = new AtomicReference<Throwable>();
        bus.setOnError(errors::set);

        stream(bus, "/boom");

        awaitFx("the failure", () -> errors.get() != null);
        assertThat(errors.get()).hasMessageContaining("HTTP 500");
        assertThat(bus.activeStreams()).isEmpty();
    }

    @Test
    void aPageReconnectsToAStreamThisBusIsNotReading() {
        serveSse("/streams/run-9/resume", List.of("Sui-Stream-Channel", "run-9"),
                patchEvent(4, "answer", "resumed"));

        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("answer", "idle"))));

        // The server says a stream is still running; this bus has never heard
        // of it (a restart, or a second window), so applyPage reconnects.
        var page = new UiPage();
        page.setActiveStreams(List.of(UiPage.ActiveStream.of(
                "run-9", baseUrl + "/streams/run-9/resume", "Import", "/imports")));
        onFxThread(() -> { bus.applyPage(page); return null; });

        awaitFx("the resumed patch", () -> {
            var node = bus.context().byId("answer");
            return node instanceof Label text && "resumed".equals(text.getText());
        });
    }

    @Test
    void aStreamAlreadyBeingReadIsNotOpenedTwice() {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/streams/run-9/resume", exchange -> {
            hits.incrementAndGet();
            respondSse(exchange, patchEvent(1, "answer", "x"));
        });

        var bus = new SuiFxEventBus();
        onFxThread(() -> bus.renderer().mount(UiStack.of(UiText.of("answer", "idle"))));

        var streams = List.of(UiPage.ActiveStream.of(
                "run-9", baseUrl + "/streams/run-9/resume", "Import", "/imports"));
        bus.reconnectMissingStreams(streams);
        awaitFx("the first connect", () -> hits.get() == 1);

        // Second page listing the same channel: already tracked, so no reopen.
        bus.reconnectMissingStreams(streams);
        bus.reconnectMissingStreams(streams);
        sleep(300);
        assertThat(hits.get()).isEqualTo(1);
    }

    private static void respondSse(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (var out = exchange.getResponseBody()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Mounts an action with a STREAM trigger at {@code path} and fires it. */
    private void stream(SuiFxEventBus bus, String path) {
        onFxThread(() -> {
            var trigger = new UiTrigger();
            trigger.setBehavior(UiTrigger.Behavior.STREAM);
            trigger.setUrl(baseUrl + path);
            var action = UiAction.primary("run", "Run");
            action.setOnClick(trigger);
            bus.dispatch(trigger, action, bus.context());
            return null;
        });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitFx(String what, BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (onFxThread(condition::getAsBoolean)) return;
            sleep(10);
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
