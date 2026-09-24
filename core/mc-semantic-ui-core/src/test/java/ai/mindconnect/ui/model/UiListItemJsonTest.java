package ai.mindconnect.ui.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A list's row on the wire: an item says {@code "type": "item"} like every
 * other node, is read back as one whether or not the JSON says so, and can
 * travel on its own inside a patch.
 */
class UiListItemJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void anItemIsWrittenWithItsType() throws Exception {
        var list = UiList.of("l", "L").item(UiList.Item.of("a", "Alpha").collapsibleClient("running", "a-sum"));
        String json = mapper.writeValueAsString(list);
        assertTrue(json.contains("\"type\":\"item\""), json);
        assertTrue(json.contains("\"collapseSummaryId\":\"a-sum\""), json);
    }

    @Test
    void anItemIsReadBackWithOrWithoutItsType() throws Exception {
        String typed = "{\"type\":\"list\",\"id\":\"l\",\"items\":[{\"type\":\"item\",\"id\":\"a\",\"label\":\"Alpha\"}]}";
        String bare = "{\"type\":\"list\",\"id\":\"l\",\"items\":[{\"id\":\"a\",\"label\":\"Alpha\",\"onClick\":{\"url\":\"/a\"}}]}";
        for (String json : new String[] { typed, bare }) {
            UiList list = (UiList) mapper.readValue(json, UiNode.class);
            assertEquals(1, list.getItems().size(), json);
            assertEquals("a", list.getItems().get(0).getId(), json);
            assertEquals("Alpha", list.getItems().get(0).getLabel(), json);
        }
        UiList bareList = (UiList) mapper.readValue(bare, UiNode.class);
        assertEquals("/a", bareList.getItems().get(0).getOnClick().getUrl());
    }

    @Test
    void anItemTravelsInsideAPatch() throws Exception {
        var patch = UiPatch.of().patch(UiPatch.Operation.replace("a", UiList.Item.of("a", "Alpha v2")));
        String json = mapper.writeValueAsString(patch);
        UiPatch back = mapper.readValue(json, UiPatch.class);
        UiNode node = back.getPatches().get(0).getNode();
        assertInstanceOf(UiList.Item.class, node);
        assertEquals("Alpha v2", ((UiList.Item) node).getLabel());
    }

    @Test
    void aMergeOnAnActionIsAMapLikeAnyOther() throws Exception {
        var op = UiPatch.Operation.merge("apply-all", Map.of("label", "Apply 3 migrations"));
        assertEquals("apply-all", op.getTargetId());
        assertEquals("Apply 3 migrations", op.getAttributes().get("label"));
    }
}
