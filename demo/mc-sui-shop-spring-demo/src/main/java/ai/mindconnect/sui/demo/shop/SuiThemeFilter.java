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
 * Reads the {@code sui-theme} cookie and forwards the resolved theme name
 * onto the {@link UiPageHtmlMessageConverter#THEME_ATTRIBUTE} request
 * attribute. The converter then picks the right stylesheet and stamps the
 * {@code <html class="sui-theme-…">} accordingly.
 *
 * <p>Sits at the highest precedence so {@link SuiModeFilter} and downstream
 * controllers see the theme through {@link SuiTheme}.
 *
 * <p>Three values are recognised: {@code light} (default), {@code dark},
 * {@code sbb}. Anything else collapses back to {@code light}. The cookie is
 * managed by {@link ThemeToggleController} so the user can cycle through
 * themes; the filter is read-only.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SuiThemeFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "sui-theme";

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK  = "dark";
    public static final String THEME_SBB   = "sbb";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        request.setAttribute(UiPageHtmlMessageConverter.THEME_ATTRIBUTE,
                resolveTheme(request));
        chain.doFilter(request, response);
    }

    /** Picks the cookie's value if recognised; falls back to light otherwise. */
    public static String resolveTheme(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return THEME_LIGHT;
        for (Cookie c : cookies) {
            if (!COOKIE_NAME.equals(c.getName())) continue;
            String v = c.getValue();
            if (THEME_DARK.equals(v) || THEME_SBB.equals(v) || THEME_LIGHT.equals(v)) {
                return v;
            }
        }
        return THEME_LIGHT;
    }
}
