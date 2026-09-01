package ai.mindconnect.ui.javafx.icons;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Loads a standalone SVG and turns it into a {@link SuiFxIcon}.
 *
 * <p>The same machinery the icon sprite uses, pointed at one document instead
 * of a symbol out of many. It is worth having because the SVG a host is most
 * likely to hand a desktop client is its logo, and a logo is very often line
 * art — the one shape of SVG this can draw.
 *
 * <p>It resolves nothing itself and follows no references: what comes back is
 * parsed as it stands. See {@link SvgShapes} for what is and is not supported.
 */
public final class SvgImage {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private SvgImage() {
    }

    /**
     * Fetches and draws {@code url}.
     *
     * @return {@code null} when it cannot be fetched, is not SVG, or uses
     *         nothing this can draw — a caller that has text to fall back on
     *         should use it
     */
    public static SuiFxIcon load(String url, String name) {
        if (url == null || url.isBlank()) return null;
        try {
            var request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "image/svg+xml")
                    .GET()
                    .build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return null;

            var document = SvgDocument.parse(response.body());
            return document.isEmpty() ? null : new SuiFxIcon(name, document);
        } catch (IOException | IllegalArgumentException unreachable) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
