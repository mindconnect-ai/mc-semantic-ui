package ai.mindconnect.ui.ssr;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestAttributes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A server-rendered page carries the request's CSRF token as meta tags, so the
 * SPA and the SSR form script can send it back without the app wiring anything.
 */
class UiPageCsrfMetaTest {

    /** The shape of Spring Security's CsrfToken, without depending on Spring Security. */
    public interface Token {
        String getHeaderName();
        String getParameterName();
        String getToken();
    }

    /** Spring hands the token out as a private class behind the public interface. */
    private static final class HiddenToken implements Token {
        private final String value;

        HiddenToken(String value) {
            this.value = value;
        }

        @Override public String getHeaderName() { return "X-CSRF-TOKEN"; }
        @Override public String getParameterName() { return "_csrf"; }
        @Override public String getToken() { return value; }
    }

    @Test
    void aRequestWithATokenGetsItsMetaTags() {
        var html = UiPageHtmlMessageConverter.csrfMetaTags(attributes(Map.of("_csrf", new HiddenToken("t\"1"))));

        assertEquals("<meta name=\"_csrf\" content=\"t&quot;1\">"
                + "<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\">"
                + "<meta name=\"_csrf_parameter\" content=\"_csrf\">", html);
    }

    @Test
    void aRequestWithoutATokenGetsNothing() {
        assertEquals("", UiPageHtmlMessageConverter.csrfMetaTags(attributes(Map.of())));
        assertEquals("", UiPageHtmlMessageConverter.csrfMetaTags(null));
        // Something else under the name is not a token.
        assertEquals("", UiPageHtmlMessageConverter.csrfMetaTags(attributes(Map.of("_csrf", "plain string"))));
    }

    private static RequestAttributes attributes(Map<String, Object> values) {
        var store = new HashMap<>(values);
        return new RequestAttributes() {
            @Override public Object getAttribute(String name, int scope) { return store.get(name); }
            @Override public void setAttribute(String name, Object value, int scope) { store.put(name, value); }
            @Override public void removeAttribute(String name, int scope) { store.remove(name); }
            @Override public String[] getAttributeNames(int scope) { return store.keySet().toArray(String[]::new); }
            @Override public void registerDestructionCallback(String name, Runnable callback, int scope) { }
            @Override public Object resolveReference(String key) { return null; }
            @Override public String getSessionId() { return "s"; }
            @Override public Object getSessionMutex() { return this; }
        };
    }
}
