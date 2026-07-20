package ai.mindconnect.sui.demo.shop;

import ai.mindconnect.ui.ssr.UiPageHtmlMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Small request-scoped accessor for the current SUI rendering mode. Reads
 * the request attribute that {@link SuiModeFilter} sets when the
 * {@code sui-mode} cookie is {@code "spa"} — that's the same flag the
 * {@link UiPageHtmlMessageConverter} reads later to decide whether to
 * emit the bootstrap script.
 *
 * <p>Used by the controllers to label the SSR/SPA toggle button in the
 * {@link ai.mindconnect.sui.demo.shop.ui.AdminPage} header.
 */
@Component
public class SuiMode {

    /** True when the current request will be rendered with SPA bootstrap. */
    public boolean isSpa() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return false;
        Object value = attrs.getAttribute(
                UiPageHtmlMessageConverter.SPA_BOOTSTRAP_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return value instanceof String s && !s.isBlank();
    }
}
