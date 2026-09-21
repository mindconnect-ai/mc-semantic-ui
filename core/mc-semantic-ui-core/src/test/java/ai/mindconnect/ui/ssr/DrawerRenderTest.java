package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A drawer on the server writes what the browser writes — drawer.test.mjs renders the same cases. */
class DrawerRenderTest {

    private static String tight(String html) {
        return html.trim().replaceAll(">\\s+<", "><");
    }

    @Test
    void theSameMarkupAsTheBrowser() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SuiServerRenderer renderer = new SuiServerRenderer();
        JsonNode cases = mapper.readTree(getClass().getResourceAsStream("/drawer/drawer-cases.json"));
        for (JsonNode c : cases) {
            UiNode node = mapper.treeToValue(c.get("node"), UiNode.class);
            // Whitespace between tags is not markup: a nested template's
            // leading newline is the only thing that ever differs there.
            assertEquals(tight(c.get("html").asText()), tight(renderer.render(node)), c.get("name").asText());
        }
    }
}
