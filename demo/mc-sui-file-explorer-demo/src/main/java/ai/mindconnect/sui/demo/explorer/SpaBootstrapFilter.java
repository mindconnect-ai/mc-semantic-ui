package ai.mindconnect.sui.demo.explorer;

import ai.mindconnect.ui.ssr.UiPageHtmlMessageConverter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs the explorer in SPA mode unconditionally: every server-rendered page
 * gets the SPA bootstrap script injected, so the {@code SuiEventBus} is present
 * to handle drag-and-drop uploads, navigation and partial patches. (The upload
 * drop zone specifically needs the bus — there is no JS-free multipart path.)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaBootstrapFilter extends OncePerRequestFilter {

    private static final String BOOTSTRAP_SCRIPT = "/spa/spa-bootstrap.js";
    private static final String EXTRA_HEAD = "<link rel=\"stylesheet\" href=\"/explorer.css\">";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        request.setAttribute(UiPageHtmlMessageConverter.SPA_BOOTSTRAP_ATTRIBUTE, BOOTSTRAP_SCRIPT);
        request.setAttribute(UiPageHtmlMessageConverter.EXTRA_HEAD_ATTRIBUTE, EXTRA_HEAD);
        chain.doFilter(request, response);
    }
}
