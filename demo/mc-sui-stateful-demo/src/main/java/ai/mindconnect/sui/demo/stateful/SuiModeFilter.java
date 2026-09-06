package ai.mindconnect.sui.demo.stateful;

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
 * Same trick as the shop demo: a {@code sui-mode=spa} cookie makes the
 * core's HTML converter add the SPA bootstrap script to every page, so the
 * same views run as a no-JS site or as a live SPA.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SuiModeFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "sui-mode";
    public static final String VALUE_SPA = "spa";
    public static final String BOOTSTRAP_SCRIPT = "/spa/spa-bootstrap.js";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isSpaMode(request)) {
            request.setAttribute(UiPageHtmlMessageConverter.SPA_BOOTSTRAP_ATTRIBUTE, BOOTSTRAP_SCRIPT);
        }
        chain.doFilter(request, response);
    }

    public static boolean isSpaMode(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && VALUE_SPA.equals(c.getValue())) return true;
        }
        return false;
    }
}
