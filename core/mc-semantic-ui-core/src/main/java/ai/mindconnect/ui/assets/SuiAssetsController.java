package ai.mindconnect.ui.assets;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Map;

/**
 * Serves the {@link SuiAssetRegistry} to the browser:
 * <ul>
 *   <li>{@code GET /sui/assets} — the resolved assets as JSON;</li>
 *   <li>{@code GET /sui/assets.js} — the ES module with
 *       {@code installAll(renderer, bus)}.</li>
 * </ul>
 * Both carry an ETag and {@code Cache-Control: no-cache}: a browser keeps its
 * copy but asks every time, and gets a 304 until the registry changes. Paths
 * are relative to the servlet context, and the hrefs inside are prefixed with
 * it. Registered by {@link SuiAssetsAutoConfiguration}.
 */
@RestController
public class SuiAssetsController {

    /** The module's media type. */
    public static final MediaType JAVASCRIPT = new MediaType("text", "javascript", java.nio.charset.StandardCharsets.UTF_8);

    private final SuiAssetRegistry registry;

    public SuiAssetsController(SuiAssetRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(path = "/sui/assets", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> assets(HttpServletRequest request, WebRequest web) {
        String etag = registry.etag(request.getContextPath(), "json");
        if (web.checkNotModified(etag)) return null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(etag)
                .body(registry.describe(request.getContextPath()));
    }

    @GetMapping(path = "/sui/assets.js")
    public ResponseEntity<String> module(HttpServletRequest request, WebRequest web) {
        String etag = registry.etag(request.getContextPath(), "js");
        if (web.checkNotModified(etag)) return null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(etag)
                .contentType(JAVASCRIPT)
                .body(registry.moduleScript(request.getContextPath()));
    }
}
