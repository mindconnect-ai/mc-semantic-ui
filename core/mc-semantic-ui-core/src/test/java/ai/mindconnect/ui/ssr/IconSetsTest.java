package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiIcon;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Icon sets on the server: the same markup the browser's default resolver
 * writes — the cases are shared with icon-sets.test.mjs — and a resolver
 * that can be wrapped rather than replaced.
 */
class IconSetsTest {

    @AfterEach
    void reset() {
        IconRenderer.setIconSets(null);
        IconRenderer.setResolver(IconRenderer.spriteResolver());
    }

    private static JsonNode cases() throws Exception {
        try (InputStream in = IconSetsTest.class.getResourceAsStream("/icons/icon-sets-cases.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    private static void useSetsOf(JsonNode fixture) {
        Map<String, String> sets = new LinkedHashMap<>();
        fixture.get("sets").fields().forEachRemaining(e -> sets.put(e.getKey(), e.getValue().asText()));
        IconRenderer.setIconSets(() -> sets);
        assertEquals(fixture.get("standard").asText(), IconRenderer.getSpriteUrl());
    }

    private static String text(JsonNode c, String field) {
        return c.hasNonNull(field) ? c.get(field).asText() : null;
    }

    @Test
    void rendersTheSharedCasesLikeTheBrowser() throws Exception {
        JsonNode fixture = cases();
        useSetsOf(fixture);
        for (JsonNode c : fixture.get("cases")) {
            assertEquals(c.get("html").asText(),
                    IconRenderer.render(c.get("token").asText(), text(c, "cssClass"), text(c, "title"), text(c, "id")),
                    c.get("name").asText());
        }
    }

    @Test
    void anIconNodeRendersThroughTheTemplateTheSameWay() throws Exception {
        useSetsOf(cases());
        String html = new SuiServerRenderer().render(UiIcon.of("g", "brand-google"));
        assertTrue(html.contains("<svg id=\"g\" class=\"sui-icon sui-icon--set\" aria-hidden=\"true\">"
                + "<use href=\"/sui-ext/brand/brand-icons.svg#brand-google\"></use></svg>"), html);
    }

    @Test
    void aResolverCanBeWrappedAndKeepsTheSets() throws Exception {
        useSetsOf(cases());
        IconRenderer.Resolver previous = IconRenderer.getResolver();
        IconRenderer.setResolver((name, cls, title, id) ->
                name.equals("star") ? "<b>*</b>" : previous.render(name, cls, title, id));
        assertEquals("<b>*</b>", IconRenderer.render("star"));
        assertTrue(IconRenderer.render("brand-google").contains("/sui-ext/brand/brand-icons.svg#brand-google"));
    }

    @Test
    void aResolverOfItsOwnReplacesEverything() throws Exception {
        useSetsOf(cases());
        IconRenderer.setResolver((name, cls, title, id) -> "<i>" + name + "</i>");
        assertEquals("<i>brand-google</i>", IconRenderer.render("brand-google"));
    }

    @Test
    void withoutSetsEveryTokenComesFromTheStandardSprite() {
        assertEquals("/sui/icons.svg", IconRenderer.spriteUrlFor("brand-google"));
    }
}
