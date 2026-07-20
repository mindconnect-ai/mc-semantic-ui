package ai.mindconnect.ui.javafx.demo;

import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiToast;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny HTTP server the demo talks to, so the JavaFX client can show the one
 * thing a purely local demo cannot: <b>the server answering with UI</b>.
 *
 * <p>Both endpoints return a serialised {@link UiPatch}. That is the whole
 * point of the vocabulary — the server sends the same {@code UiNode} tree the
 * browser would get, and the JavaFX client applies it with no endpoint-specific
 * code on the client side at all. {@code GET /api/inventory} needs zero handler
 * in {@link DemoApplication}; the {@code APPLY_RESPONSE} behaviour does it.
 *
 * <p><b>Why not Spark.</b> {@code com.sun.net.httpserver} ships with the JDK,
 * so the demo stays dependency-free — dragging Jetty into a core-area module
 * just to show three endpoints would be a poor trade. Swapping in Spark later
 * is a contained change: the handlers only build patches and write JSON.
 *
 * <p>It binds to port 0, so the OS picks a free one and two running demos never
 * collide. Ask for {@link #url(String)} rather than assuming a port.
 */
public class DemoServer {

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    /** The server's "database", separate from the client's own order list. */
    private final List<Map<String, Object>> inventory = new ArrayList<>(List.of(
            item("SKU-100", "Mechanical keyboard", 42),
            item("SKU-200", "27\" monitor", 7),
            item("SKU-300", "USB-C dock", 0)
    ));

    private final List<String> uploads = new ArrayList<>();

    public DemoServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/inventory", this::inventory);
        server.createContext("/api/files", this::upload);
        server.setExecutor(null); // the built-in default executor is enough here
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** Absolute url for a path on this server, e.g. {@code url("/api/inventory")}. */
    public String url(String path) {
        return "http://127.0.0.1:" + port() + path;
    }

    public void stop() {
        server.stop(0);
    }

    // ── endpoints ─────────────────────────────────────────────────────────

    /** Answers with a patch that swaps the client's inventory panel for a table. */
    private void inventory(HttpExchange exchange) throws IOException {
        var table = UiTable.of("inventory-table", "Inventory (from the server)")
                .column(UiColumn.text("sku", "SKU").asSortable())
                .column(UiColumn.text("name", "Product"))
                .column(UiColumn.number("stock", "In stock"));
        inventory.forEach(table::row);

        var panel = UiStack.of(table);
        panel.setId("inventory-panel");

        respond(exchange, UiPatch.of()
                .patch(UiPatch.Operation.replace("inventory-panel", panel))
                .toast(UiToast.success(inventory.size() + " items loaded").title("Inventory")));
    }

    /**
     * Accepts the multipart body the {@code UPLOAD} behaviour posts and answers
     * with a patch listing what arrived.
     */
    private void upload(HttpExchange exchange) throws IOException {
        var body = exchange.getRequestBody().readAllBytes();
        var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        var filename = filenameOf(new String(body, StandardCharsets.UTF_8));

        uploads.add(filename == null ? "(unnamed)" : filename);

        var summary = new StringBuilder("Received by the server:\n");
        uploads.forEach(name -> summary.append("  • ").append(name).append('\n'));
        summary.append("\nContent-Type was: ").append(contentType)
                .append("\nBody size: ").append(body.length).append(" bytes");

        var panel = UiStack.of(UiText.of(summary.toString()));
        panel.setId("upload-result");

        respond(exchange, UiPatch.of()
                .patch(UiPatch.Operation.replace("upload-result", panel))
                .toast(UiToast.success("Server stored " + filename).title("Upload")));
    }

    // ── plumbing ──────────────────────────────────────────────────────────

    /**
     * Pulls the filename out of a multipart body's first part header. Enough
     * for a demo — a real endpoint would use a proper multipart parser rather
     * than trusting that the header is inside the first few hundred bytes.
     */
    private String filenameOf(String body) {
        var marker = "filename=\"";
        int start = body.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = body.indexOf('"', start);
        return end < 0 ? null : body.substring(start, end);
    }

    private void respond(HttpExchange exchange, UiPatch patch) throws IOException {
        var json = mapper.writeValueAsBytes(patch);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.length);
        try (var out = exchange.getResponseBody()) {
            out.write(json);
        }
    }

    private static Map<String, Object> item(String sku, String name, int stock) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", sku);
        row.put("sku", sku);
        row.put("name", name);
        row.put("stock", stock);
        return row;
    }
}
