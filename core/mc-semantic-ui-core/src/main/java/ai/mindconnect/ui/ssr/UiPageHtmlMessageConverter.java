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
import java.lang.reflect.Modifier;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /** Element the hybrid document parks its model in, for the SPA to seed from. */
    public static final String MODEL_ELEMENT_ID = "sui-model";

    /**
     * Request attribute selecting which theme stylesheet to load. Value is
     * a {@link String} matching one of the built-in theme names: {@code "light"}
     * (default), {@code "sbb"}, or one of the overlays {@code "dark"},
     * {@code "compact"}, {@code "clody"}, {@code "gipiti"}, {@code "sorbet"},
     * {@code "amethyst"}. Unknown / null falls back to {@code light}.
     *
     * <p>Effect on the rendered HTML:
     * <ul>
     *   <li>{@code light}: only {@code /sui/sui.css}.</li>
     *   <li>an overlay, e.g. {@code dark}: {@code /sui/sui.css} +
     *       {@code /sui/sui-dark.css} — the theme sheet only restyles on top,
     *       scoped to its class on {@code <html>}.</li>
     *   <li>{@code sbb}: {@code /sui/sui-sbb.css} <strong>instead of</strong>
     *       sui.css — a fully self-contained stylesheet.</li>
     * </ul>
     * The {@code <html>} element also gets a {@code class="sui-theme-…"} so
     * the theme CSS scope rules match.
     */
    public static final String THEME_ATTRIBUTE = "mindconnect.sui.theme";

    private static final String THEME_LIGHT = "light";
    private static final String THEME_SBB   = "sbb";
    /** Themes that layer on sui.css, each as {@code /sui/sui-<name>.css}. */
    private static final Set<String> OVERLAY_THEMES =
            Set.of("dark", "compact", "clody", "gipiti", "sorbet", "amethyst");

    private final SuiServerRenderer renderer;
    /**
     * Serialises the model for {@link #renderModel}. Extension node types
     * register through Jackson's ServiceLoader, so a page carrying a chart or
     * a markdown block seeds as readily as one that does not.
     */
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    /**
     * The stylesheets of the asset registry, linked in every page's head so
     * an extension's CSS is there before the first paint. Null without one.
     */
    private final ai.mindconnect.ui.assets.SuiAssetRegistry assets;

    public UiPageHtmlMessageConverter(SuiServerRenderer renderer) {
        this(renderer, null);
    }

    /** With the stylesheets of {@code assets} linked in every page's head. */
    public UiPageHtmlMessageConverter(SuiServerRenderer renderer, ai.mindconnect.ui.assets.SuiAssetRegistry assets) {
        super(StandardCharsets.UTF_8, MediaType.TEXT_HTML);
        this.renderer = renderer;
        this.assets = assets;
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
        String csrfMeta = csrfMetaTags(RequestContextHolder.getRequestAttributes());
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
        String model = bootstrapUrl == null ? "" : renderModel(page);

        String document = "<!DOCTYPE html>\n<html class=\"sui-theme-" + theme + "\"><head><meta charset=\"UTF-8\">"
                + themeStylesheets(theme)
                + (assets == null ? "" : assets.headTags(currentContextPath()))
                + csrfMeta
                + extraHead
                + "</head><body>"
                + body
                + dialogs
                + toasts
                + model
                + scriptTag
                + "</body></html>";
        outputMessage.getBody().write(document.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Emits the page's own model as JSON, for the SPA to seed itself from.
     *
     * <p>A hybrid page arrives as finished HTML: the client never rendered it,
     * so it knows what every element looks like and nothing about what any of
     * them <em>are</em>. That is enough until a patch wants to change part of
     * a node and leave the rest — {@code MERGE} — which needs the rest.
     *
     * <p>One blob at the end of the body rather than a model on every element:
     * the tree is already being serialised on this request, and writing it
     * once costs a fraction of writing each node's share of it into an
     * attribute. Only hybrid pages carry it; a pure-SSR page has no client to
     * read it.
     *
     * <p>It goes in a {@code <script type="application/json">}, where the only
     * character that can end the block early is a literal {@code </script>} in
     * the data — so that one sequence is escaped and nothing else has to be.
     */
    private String renderModel(UiPage page) {
        try {
            var json = mapper.writeValueAsString(page.getNode());
            return "<script type=\"application/json\" id=\"" + MODEL_ELEMENT_ID + "\">"
                    + json.replace("</", "<\\/")
                    + "</script>";
        } catch (Exception e) {
            // A page that renders but cannot be serialised is still a page.
            // Losing MERGE beats losing the screen.
            return "";
        }
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
            // CSRF: a POST form submits the token the page carries as a hidden field
            + "var cm=document.querySelector('meta[name=\"_csrf\"]');"
            + "if(cm){var cp=document.querySelector('meta[name=\"_csrf_parameter\"]');"
            + "var pn=(cp&&cp.content)||'_csrf';"
            + "Array.prototype.forEach.call(document.querySelectorAll('form'),function(f){"
            + "if((f.getAttribute('method')||'get').toLowerCase()==='get'||f.querySelector('input[name=\"'+pn+'\"]'))return;"
            + "var i=document.createElement('input');i.type='hidden';i.name=pn;i.value=cm.content;f.appendChild(i);"
            + "});}"
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
     * Picks the right stylesheet(s) for the theme. Light is the default; an
     * overlay stacks its sheet on top of sui.css; sbb replaces sui.css entirely.
     */
    static String themeStylesheets(String theme) {
        if (THEME_SBB.equals(theme)) return "<link rel=\"stylesheet\" href=\"/sui/sui-sbb.css\">";
        String base = "<link rel=\"stylesheet\" href=\"/sui/sui.css\">";
        return OVERLAY_THEMES.contains(theme)
                ? base + "<link rel=\"stylesheet\" href=\"/sui/sui-" + theme + ".css\">"
                : base;
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

    /** Request attribute under which Spring Security exposes the request's {@code CsrfToken}. */
    static final String CSRF_ATTRIBUTE = "_csrf";

    /**
     * The request's CSRF token as {@code <meta name="_csrf">}, {@code _csrf_header} and
     * {@code _csrf_parameter} — Spring Security's convention for server-rendered pages —
     * or nothing when the request carries no token.
     *
     * <p>The page needs no wiring for them to matter: the SPA's event bus sends the token
     * in that header on every unsafe request, and the SSR script adds it as a hidden field
     * to every POST form, so a plain form submit passes the same check.
     *
     * <p>Read by reflection, not through Spring Security's {@code CsrfToken} type: the core
     * does not depend on Spring Security, and an app without it simply has no such
     * attribute. Reading the token is also what makes a deferred one real.
     */
    static String csrfMetaTags(RequestAttributes attrs) {
        if (attrs == null) return "";
        Object token = attrs.getAttribute(CSRF_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (token == null) return "";
        String value = invokeString(token, "getToken");
        if (value == null || value.isBlank()) return "";
        String header = invokeString(token, "getHeaderName");
        String parameter = invokeString(token, "getParameterName");
        return "<meta name=\"_csrf\" content=\"" + escapeAttr(value) + "\">"
                + (header == null ? "" : "<meta name=\"_csrf_header\" content=\"" + escapeAttr(header) + "\">")
                + (parameter == null ? "" : "<meta name=\"_csrf_parameter\" content=\"" + escapeAttr(parameter) + "\">");
    }

    /**
     * Calls a public no-argument method through a public type that declares it. Spring
     * Security hands out its token as a private class implementing the public
     * {@code CsrfToken} interface, and a method looked up on the private class itself
     * cannot be invoked from here.
     */
    private static String invokeString(Object target, String method) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Class<?> candidate : publicTypes(type)) {
                try {
                    Object result = candidate.getMethod(method).invoke(target);
                    return result instanceof String s ? s : null;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // not declared here, or not callable through this type; try the next
                }
            }
        }
        return null;
    }

    private static List<Class<?>> publicTypes(Class<?> type) {
        var types = new ArrayList<Class<?>>();
        if (Modifier.isPublic(type.getModifiers())) types.add(type);
        for (Class<?> i : type.getInterfaces()) {
            if (Modifier.isPublic(i.getModifiers())) types.add(i);
        }
        return types;
    }

    /** The servlet context path of the current request, {@code ""} at the root or outside a request. */
    private static String currentContextPath() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            return servlet.getRequest().getContextPath();
        }
        return "";
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
        return THEME_SBB.equals(s) || OVERLAY_THEMES.contains(s) ? s : THEME_LIGHT;
    }

    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
