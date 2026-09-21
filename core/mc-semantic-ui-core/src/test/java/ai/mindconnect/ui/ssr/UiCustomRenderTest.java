package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiCustom;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plugin's node on the server: its template when a jar ships one, else the placeholder the SPA replaces. */
class UiCustomRenderTest {

    private final SuiServerRenderer renderer = new SuiServerRenderer();

    @Test
    void withoutATemplateItIsThePlaceholderAndThePageStands() {
        UiStack stack = UiStack.of(UiText.of("before", "Before"),
                UiCustom.of("chat", "chat-widget").cssClass("wide").prop("session", "s1"),
                UiText.of("after", "After"));
        String html = renderer.render(stack);
        assertTrue(html.contains("<div id=\"chat\" class=\"sui-custom-missing wide\" data-type=\"chat-widget\"></div>"), html);
        assertTrue(html.contains("Before") && html.contains("After"), html);
        assertTrue(!html.contains("<pre>") && !html.contains("s1"), "no dump of the node's data: " + html);
    }

    @Test
    void theSameMarkupAsTheBrowser() {
        // renderMissing() in renderer.ts writes exactly this; ui-custom.test.mjs checks the same string.
        assertEquals("<div id=\"x&quot;1\" class=\"sui-custom-missing\" data-type=\"x-demo\"></div>",
                renderer.render(UiCustom.of("x\"1", "x-demo").prop("a", 1)));
    }

    @Test
    void aTemplateOfTheTypeRendersItWithItsProperties() {
        String html = renderer.render(UiCustom.of("t", "x-templated").prop("greeting", "Hello").prop("who", "<Ada>"));
        assertEquals("<section id=\"t\" class=\"x-templated\">Hello, &lt;Ada&gt;</section>", html.trim());
    }
}
