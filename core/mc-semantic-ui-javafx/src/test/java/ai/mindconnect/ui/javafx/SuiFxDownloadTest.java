package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiTrigger;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A download that keeps the name the server gave it.
 *
 * <p>The name is most of what makes a downloaded file useful, and a temp file
 * cannot carry one — {@code Files.createTempFile} names the file itself. So
 * the bytes get a folder of their own and keep the name, read from
 * {@code Content-Disposition} or, failing that, from the url. Same two-step
 * the browser renderer makes, so the same button saves the same name on the
 * desktop as on the web.
 */
class SuiFxDownloadTest {

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
        Platform.setImplicitExit(false);
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

    private void serve(String path, String disposition, String body) {
        server.createContext(path, exchange -> {
            if (disposition != null) {
                exchange.getResponseHeaders().add("Content-Disposition", disposition);
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    /** Runs a DOWNLOAD and hands back the file the handler was given. */
    private File download(String path) throws Exception {
        var bus = new SuiFxEventBus();
        var got = new AtomicReference<File>();
        var landed = new CountDownLatch(1);
        bus.setDownloadHandler(file -> {
            got.set(file);
            landed.countDown();
        });
        bus.dispatch(UiTrigger.download(baseUrl + path), null, bus.renderer().context());
        assertThat(landed.await(10, TimeUnit.SECONDS))
                .describedAs("the download to reach the handler")
                .isTrue();
        return got.get();
    }

    @Test
    void theServersFilenameIsKept() throws Exception {
        serve("/files/1", "attachment; filename=\"donor-report.pdf\"", "hello");

        var file = download("/files/1");

        assertThat(file.getName()).isEqualTo("donor-report.pdf");
        assertThat(Files.readString(file.toPath())).isEqualTo("hello");
    }

    @Test
    void anEncodedFilenameIsDecoded() throws Exception {
        serve("/files/2", "attachment; filename*=UTF-8''donor%20report.pdf", "x");

        assertThat(download("/files/2").getName()).isEqualTo("donor report.pdf");
    }

    @Test
    void withoutAHeaderTheUrlNamesTheFile() throws Exception {
        serve("/workspace/notes.md", null, "# notes");

        assertThat(download("/workspace/notes.md").getName()).isEqualTo("notes.md");
    }

    @Test
    void aFilenameCannotSayWhereTheFileGoes() throws Exception {
        // A name is a name. Anything that reads as a path is stripped to its
        // last segment, so a server cannot aim a download at ~/.ssh.
        serve("/files/3", "attachment; filename=\"../../evil.sh\"", "rm -rf /");

        var file = download("/files/3");

        assertThat(file.getName()).isEqualTo("evil.sh");
        assertThat(file.getParentFile().getName()).startsWith("sui-download-");
    }
}
