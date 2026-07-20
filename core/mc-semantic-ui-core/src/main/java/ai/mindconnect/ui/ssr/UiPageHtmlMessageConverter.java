package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiToast;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Renders {@link UiPage} return values to {@code text/html} via the
 * {@link SuiServerRenderer} when the client's {@code Accept} header asks for
 * HTML. Slots in alongside Spring's regular JSON converter — Spring picks
 * whichever converter the negotiated content type matches, so the same
 * controller method serves both modes:
 *
 * <ul>
 *   <li>{@code Accept: text/html}  → this converter (server-side HTML)</li>
 *   <li>{@code Accept: application/json} → Jackson converter (SPA / JSON)</li>
 * </ul>
 *
 * <h2>SPA takeover</h2>
 * A controller that wants the SSR-first / SPA-takeover flow sets the
 * request attribute {@value #SPA_BOOTSTRAP_ATTRIBUTE} to the URL of a
 * bootstrap script. The converter then emits the same fully-rendered
 * markup BUT inside an addressable {@code <div id="sui-root">} wrapper
 * and adds a {@code <script type="module">} tag at the end of the body.
 * The script is responsible for attaching a {@code SuiEventBus} to the
 * root — from there, subsequent navigations switch to JSON / SPA
 * automatically. Reload still works because the URL is real (the next
 * GET re-runs SSR).
 *
 * <p>Read side is intentionally not supported: {@code UiPage} is a
 * server-to-client document, never a request body.
 */
public class UiPageHtmlMessageConverter extends AbstractHttpMessageConverter<UiPage> {

    /**
     * Request attribute that, when set to a non-blank string, makes the
     * converter emit a SPA-takeover-ready HTML document: addressable
     * root + bootstrap script. The string value is the URL of the
     * {@code <script type="module">} to load.
     */
    public static final String SPA_BOOTSTRAP_ATTRIBUTE = "mindconnect.sui.spa.bootstrap";

    /**
     * Request attribute that, when set to a non-null string, is injected
     * verbatim into the {@code <head>} of the response HTML. Used by apps
     * to add extra CSS/JS/meta tags on a per-request basis without
     * overriding the whole document template. Empty string = no addition.
     */
    public static final String EXTRA_HEAD_ATTRIBUTE = "mindconnect.sui.extra.head";

    /**
     * Request attribute selecting which theme stylesheet to load. Value is
     * a {@link String} matching one of the built-in theme names: {@code "light"}
     * (default), {@code "dark"}, {@code "sbb"}. Unknown / null falls back to
     * {@code light}.
     *
     * <p>Effect on the rendered HTML:
     * <ul>
     *   <li>{@code light}: only {@code /sui/sui.css}.</li>
     *   <li>{@code dark}: {@code /sui/sui.css} + {@code /sui/sui-dark.css}
     *       (override-on-top — dark only sets CSS custom properties).</li>
     *   <li>{@code sbb}: {@code /sui/sui-sbb.css} <strong>instead of</strong>
     *       sui.css — a fully self-contained stylesheet.</li>
     * </ul>
     * The {@code <html>} element also gets a {@code class="sui-theme-…"} so
     * the theme CSS scope rules match.
     */
    public static final String THEME_ATTRIBUTE = "mindconnect.sui.theme";

    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK  = "dark";
    private static final String THEME_SBB   = "sbb";

    private final SuiServerRenderer renderer;

    public UiPageHtmlMessageConverter(SuiServerRenderer renderer) {
        super(StandardCharsets.UTF_8, MediaType.TEXT_HTML);
        this.renderer = renderer;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return UiPage.class.isAssignableFrom(clazz);
    }

    @Override
    protected UiPage readInternal(Class<? extends UiPage> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException(
                "UiPage is server-rendered; reading it from HTML is not supported", inputMessage);
    }

    @Override
    protected void writeInternal(UiPage page, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        String html = renderer.renderPage(page);
        String bootstrapUrl = currentBootstrapUrl();
        String extraHead = currentExtraHead();
        String theme = currentTheme();

        // The body always wraps the rendered HTML in #sui-root so a SPA
        // bootstrap (if loaded) can attach to it. The wrapper is harmless
        // in pure SSR — it's a single div around the content.
        String body = "<div id=\"sui-root\">" + html + "</div>";
        String dialogs = renderDialogHost(page);
        String toasts = renderToastContainer(page);
        String scriptTag = bootstrapUrl == null
                ? SSR_AUTO_WIRING_SCRIPT
                : "<script type=\"module\" src=\"" + escapeAttr(bootstrapUrl) + "\"></script>";

        String document = "<!DOCTYPE html>\n<html class=\"sui-theme-" + theme + "\"><head><meta charset=\"UTF-8\">"
                + themeStylesheets(theme)
                + extraHead
                + "</head><body>"
                + body
                + dialogs
                + toasts
                + scriptTag
                + "</body></html>";
        outputMessage.getBody().write(document.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Emits the body-level {@code #sui-dialogs} host and paints each open
     * dialog ({@link UiPage#getDialogs()}) into it via the node renderer. The
     * host is always present (even when empty) so the SPA EventBus can find it
     * by id to APPEND / REMOVE dialogs later. Each {@code UiDialog} renders as
     * its own {@code .sui-dialog-host} (fixed-position overlay), so multiple
     * dialogs stack by id.
     */
    private String renderDialogHost(UiPage page) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"sui-dialogs\" class=\"sui-dialogs\">");
        if (page.getDialogs() != null) {
            for (UiDialog d : page.getDialogs()) {
                sb.append(renderer.render(d));
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Emits the toast container with each toast as a child {@code <div>}.
     * The container is always present (even when empty) so the SPA
     * EventBus can find it later by id to append new toasts. The inline
     * auto-wiring script handles auto-dismiss in pure SSR.
     */
    private static String renderToastContainer(UiPage page) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"sui-toast-container\" class=\"sui-toast-container\">");
        if (page.getToasts() != null) {
            for (UiToast t : page.getToasts()) {
                sb.append(renderToast(t));
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * One toast → one {@code .sui-toast} card. Carries {@code data-duration-ms}
     * so the auto-dismiss script can read the per-toast timeout; sticky toasts
     * (duration ≤ 0) get {@code data-duration-ms="0"} which the script treats
     * as "don't dismiss".
     */
    private static String renderToast(UiToast t) {
        String level = t.getLevel() == null ? "INFO" : t.getLevel().name();
        String cls   = "sui-toast sui-toast--" + level.toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"").append(cls).append("\"")
          .append(" data-duration-ms=\"").append(Math.max(0, t.getDurationMs())).append("\"")
          .append(" role=\"status\">");
        if (t.getTitle() != null && !t.getTitle().isBlank()) {
            sb.append("<div class=\"sui-toast-title\">").append(escapeHtml(t.getTitle())).append("</div>");
        }
        sb.append("<div class=\"sui-toast-message\">").append(escapeHtml(t.getMessage() == null ? "" : t.getMessage())).append("</div>");
        sb.append("<button type=\"button\" class=\"sui-toast-close\" aria-label=\"Close\">×</button>");
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Inline auto-wiring script shipped with every pure-SSR response (i.e. no
     * SPA bootstrap). Keeps a couple of opt-in behaviours working without
     * pulling in the full EventBus:
     *
     * <ul>
     *   <li>{@code data-submit-on-change} on a control → fire
     *       {@code form.requestSubmit()} on change. Lets {@code UiField.submitOnChange()}
     *       drive "instant" controls like a theme picker dropdown in pure
     *       SSR mode too.</li>
     *   <li>{@code .sui-toast[data-duration-ms]} → schedule self-removal
     *       after that many milliseconds (skipped when ≤ 0 = sticky).</li>
     *   <li>{@code .sui-toast-close} click → remove parent toast.</li>
     * </ul>
     *
     * Tiny enough to inline; replaced by the SPA bootstrap script when SPA
     * mode is active (the EventBus handles the same behaviours).
     */
    private static final String SSR_AUTO_WIRING_SCRIPT =
            "<script>(function(){"
            // submit-on-change
            + "document.addEventListener('change',function(e){"
            + "var t=e.target;"
            + "if(!t||t.dataset.submitOnChange!=='true')return;"
            + "var f=t.form||t.closest('form');"
            + "if(f&&f.requestSubmit)f.requestSubmit();"
            + "});"
            // toast close button
            + "document.addEventListener('click',function(e){"
            + "var b=e.target&&e.target.closest&&e.target.closest('.sui-toast-close');"
            + "if(!b)return;"
            + "var t=b.closest('.sui-toast');"
            + "if(t)t.remove();"
            + "});"
            // toast auto-dismiss
            + "function arm(t){"
            + "var d=parseInt(t.dataset.durationMs||'0',10);"
            + "if(!d||d<=0)return;"
            + "setTimeout(function(){t.classList.add('sui-toast--leaving');"
            + "setTimeout(function(){t.remove();},200);},d);"
            + "}"
            + "Array.prototype.forEach.call(document.querySelectorAll('.sui-toast'),arm);"
            + "})();</script>";

    /**
     * Picks the right stylesheet(s) for the theme. Light is the default; dark
     * stacks an override on top of sui.css; sbb replaces sui.css entirely.
     */
    private static String themeStylesheets(String theme) {
        return switch (theme) {
            case THEME_DARK -> "<link rel=\"stylesheet\" href=\"/sui/sui.css\">"
                             + "<link rel=\"stylesheet\" href=\"/sui/sui-dark.css\">";
            case THEME_SBB  -> "<link rel=\"stylesheet\" href=\"/sui/sui-sbb.css\">";
            default         -> "<link rel=\"stylesheet\" href=\"/sui/sui.css\">";
        };
    }

    /**
     * Reads {@link #SPA_BOOTSTRAP_ATTRIBUTE} off the current request, if
     * any. Returns the URL when present and non-blank, else {@code null}
     * (= pure SSR, no SPA script injected).
     */
    private static String currentBootstrapUrl() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        Object value = attrs.getAttribute(SPA_BOOTSTRAP_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (value instanceof String s && !s.isBlank()) return s;
        return null;
    }

    /**
     * Reads {@link #EXTRA_HEAD_ATTRIBUTE} off the current request. Returns
     * the verbatim HTML to splice into {@code <head>}, or {@code ""} when
     * unset / blank.
     */
    private static String currentExtraHead() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "";
        Object value = attrs.getAttribute(EXTRA_HEAD_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (value instanceof String s && !s.isBlank()) return s;
        return "";
    }

    /**
     * Reads {@link #THEME_ATTRIBUTE} off the current request. Returns one of
     * the known theme ids, defaulting to {@code light} when unset or unknown.
     */
    private static String currentTheme() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return THEME_LIGHT;
        Object value = attrs.getAttribute(THEME_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof String s)) return THEME_LIGHT;
        return switch (s) {
            case THEME_DARK, THEME_SBB, THEME_LIGHT -> s;
            default -> THEME_LIGHT;
        };
    }

    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
