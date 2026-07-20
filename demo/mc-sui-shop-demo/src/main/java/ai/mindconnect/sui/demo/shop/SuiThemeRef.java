package ai.mindconnect.sui.demo.shop;

import ai.mindconnect.ui.ssr.UiPageHtmlMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Request-scoped accessor for the current theme name. Mirrors
 * {@link SuiMode} for the SSR/SPA mode but on the theme axis.
 *
 * <p>Reads what {@link SuiThemeFilter} stashed onto the request as
 * {@link UiPageHtmlMessageConverter#THEME_ATTRIBUTE}. Used by the admin
 * shell so the theme switcher button can show "Switch to Dark" / "Switch
 * to SBB" depending on what's currently active.
 */
@Component
public class SuiThemeRef {

    /** Current theme name, never null — defaults to {@code light}. */
    public String current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return SuiThemeFilter.THEME_LIGHT;
        Object value = attrs.getAttribute(
                UiPageHtmlMessageConverter.THEME_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        if (value instanceof String s) return s;
        return SuiThemeFilter.THEME_LIGHT;
    }
}
