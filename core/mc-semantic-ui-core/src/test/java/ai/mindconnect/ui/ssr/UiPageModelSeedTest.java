package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hybrid page carries its own model.
 *
 * <p>Such a page arrives as finished HTML: the client draws none of it, so it
 * knows what everything looks like and nothing about what any of it is. That
 * is enough until a {@code MERGE} wants to change part of a node and leave the
 * rest — which needs the rest. The server parks the tree in the document for
 * exactly that, and only when there is a client to read it.
 */
class UiPageModelSeedTest {

    private final UiPageHtmlMessageConverter converter =
            new UiPageHtmlMessageConverter(new SuiServerRenderer());

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Renders a page, with {@code bootstrapUrl} deciding hybrid vs pure SSR. */
    private String render(UiPage page, String bootstrapUrl) throws Exception {
        RequestContextHolder.setRequestAttributes(bootstrapUrl == null
                ? attributes(Map.of())
                : attributes(Map.of(UiPageHtmlMessageConverter.SPA_BOOTSTRAP_ATTRIBUTE, bootstrapUrl)));

        var body = new ByteArrayOutputStream();
        converter.write(page, null, new HttpOutputMessage() {
            @Override
            public OutputStream getBody() {
                return body;
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        });
        return body.toString(StandardCharsets.UTF_8);
    }

    private static UiPage pageSaying(String text) {
        var page = new UiPage();
        page.setNode(UiStack.of(UiText.of("greeting", text)));
        return page;
    }

    @Test
    void aHybridPageParksItsModelInTheDocument() throws Exception {
        var html = render(pageSaying("hello"), "/sui/bootstrap.js");

        assertTrue(html.contains("<script type=\"application/json\" id=\"sui-model\">"), html);
        assertTrue(html.contains("\"id\":\"greeting\""), html);
        // An addition, not a replacement: the page is still server-rendered.
        assertTrue(html.contains("hello"), html);
    }

    @Test
    void aPureSsrPageCarriesNoModel() throws Exception {
        var html = render(pageSaying("hello"), null);

        // No client to read it, so it would be weight for nothing.
        assertFalse(html.contains("id=\"sui-model\""), html);
        assertTrue(html.contains("hello"), html);
    }

    @Test
    void aClosingScriptTagInTheDataCannotEndTheBlockEarly() throws Exception {
        var page = new UiPage();
        // The one sequence that can break out of a JSON script block, which is
        // why it is the one sequence that gets escaped.
        page.setNode(UiText.of("t", "</script><img src=x onerror=alert(1)>"));

        var html = render(page, "/sui/bootstrap.js");
        var block = html.substring(html.indexOf("id=\"sui-model\""));
        block = block.substring(0, block.indexOf("</script>"));

        assertFalse(block.contains("</script>"), block);
        assertTrue(block.contains("<\\/script>"), block);
    }

    /** The smallest RequestAttributes the converter's three lookups need. */
    private static RequestAttributes attributes(Map<String, Object> values) {
        var store = new HashMap<>(values);
        return new RequestAttributes() {
            @Override
            public Object getAttribute(String name, int scope) {
                return store.get(name);
            }

            @Override
            public void setAttribute(String name, Object value, int scope) {
                store.put(name, value);
            }

            @Override
            public void removeAttribute(String name, int scope) {
                store.remove(name);
            }

            @Override
            public String[] getAttributeNames(int scope) {
                return store.keySet().toArray(new String[0]);
            }

            @Override
            public void registerDestructionCallback(String name, Runnable callback, int scope) {
            }

            @Override
            public Object resolveReference(String key) {
                return null;
            }

            @Override
            public String getSessionId() {
                return "test";
            }

            @Override
            public Object getSessionMutex() {
                return this;
            }
        };
    }
}
