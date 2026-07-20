package ai.mindconnect.sui.demo.shop;

import ai.mindconnect.ui.ssr.UiPageHtmlMessageConverter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Decides whether the current request should render in SPA mode by looking
 * at the {@code sui-mode} cookie. When the cookie's value is
 * {@code "spa"}, the {@link UiPageHtmlMessageConverter} is signalled (via
 * a request attribute) to wrap the HTML response in the SPA-bootstrap
 * shell — a {@code <div id="sui-root">} plus a {@code <script type="module">}
 * that attaches the {@code SuiEventBus} on load.
 *
 * <p>URLs stay identical between modes ({@code /admin/products} in both),
 * so bookmarks and shared links work in either flavour. The mode is a
 * per-browser-session preference, toggled by {@link ModeToggleController}.
 *
 * <p>Default is SSR: a fresh browser with no cookie set sees the plain
 * server-rendered version. The user opts in to SPA mode via the toggle
 * button in the admin header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SuiModeFilter extends OncePerRequestFilter {

    /** Cookie name read on every request to decide the mode. */
    public static final String COOKIE_NAME = "sui-mode";

    /** Cookie value indicating SPA-takeover. Anything else (or absent) = SSR. */
    public static final String VALUE_SPA = "spa";

    /** Where the bootstrap script that attaches the EventBus lives. */
    public static final String BOOTSTRAP_SCRIPT = "/spa/spa-bootstrap.js";

    /**
     * Extra HTML injected into every page's {@code <head>}. The demo ships
     * a tiny {@code demo.css} that styles the SSR/SPA toggle button — the
     * framework knows nothing about it, so we inject the link here per
     * request via {@link UiPageHtmlMessageConverter#EXTRA_HEAD_ATTRIBUTE}.
     * Both modes need the styling, so we set it unconditionally.
     */
    private static final String EXTRA_HEAD =
            "<link rel=\"stylesheet\" href=\"/demo.css\">";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (isSpaMode(request)) {
            request.setAttribute(UiPageHtmlMessageConverter.SPA_BOOTSTRAP_ATTRIBUTE,
                    BOOTSTRAP_SCRIPT);
        }
        request.setAttribute(UiPageHtmlMessageConverter.EXTRA_HEAD_ATTRIBUTE, EXTRA_HEAD);
        chain.doFilter(request, response);
    }

    private static boolean isSpaMode(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && VALUE_SPA.equals(c.getValue())) {
                return true;
            }
        }
        return false;
    }
}
