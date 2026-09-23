package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A drawer group on the server writes what the browser writes — the drawers
 * inside it with the group's edge, scope and mode, none with a grip of its
 * own. drawer-group.test.mjs renders the same cases.
 */
class DrawerGroupRenderTest {

    private static String tight(String html) {
        return html.trim().replaceAll(">\\s+<", "><");
    }

    @Test
    void theSameMarkupAsTheBrowser() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SuiServerRenderer renderer = new SuiServerRenderer();
        JsonNode cases = mapper.readTree(getClass().getResourceAsStream("/drawer/drawer-group-cases.json"));
        for (JsonNode c : cases) {
            UiNode node = mapper.treeToValue(c.get("node"), UiNode.class);
            // Whitespace between tags is not markup: a nested template's
            // leading newline is the only thing that ever differs there.
            assertEquals(tight(c.get("html").asText()), tight(renderer.render(node)), c.get("name").asText());
        }
    }
}
