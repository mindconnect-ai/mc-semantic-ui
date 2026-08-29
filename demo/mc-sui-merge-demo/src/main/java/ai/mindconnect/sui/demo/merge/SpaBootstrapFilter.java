package ai.mindconnect.sui.demo.merge;

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
 * Runs the demo as a hybrid page: server-rendered HTML with the SPA bus
 * attached on top.
 *
 * <p>That combination is the interesting one for {@code MERGE}. The client
 * never built this tree — it was handed finished HTML — so it knows what every
 * element looks like and nothing about what any of them <em>are</em>. A merge
 * needs the rest of the node it is changing, and the rest is exactly what a
 * page like this has no idea about.
 *
 * <p>Which is why {@code UiPageHtmlMessageConverter} writes the page's own
 * model into a {@code <script type="application/json" id="sui-model">} at the
 * end of the body, and the bus seeds itself from it on startup. View source on
 * this page and it is there to read.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaBootstrapFilter extends OncePerRequestFilter {

    private static final String BOOTSTRAP_SCRIPT = "/spa/spa-bootstrap.js";
    private static final String EXTRA_HEAD = "<link rel=\"stylesheet\" href=\"/merge-demo.css\">";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        request.setAttribute(UiPageHtmlMessageConverter.SPA_BOOTSTRAP_ATTRIBUTE, BOOTSTRAP_SCRIPT);
        request.setAttribute(UiPageHtmlMessageConverter.EXTRA_HEAD_ATTRIBUTE, EXTRA_HEAD);
        chain.doFilter(request, response);
    }
}
