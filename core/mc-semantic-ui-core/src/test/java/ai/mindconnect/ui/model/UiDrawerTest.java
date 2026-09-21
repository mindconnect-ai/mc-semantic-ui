package ai.mindconnect.ui.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UiDrawer on the wire: what it says, what it leaves out, what it refuses. */
class UiDrawerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theChatDrawerGoesOverTheWire() throws Exception {
        UiDrawer drawer = UiDrawer.of("ai-chat", "Draft with AI", UiCustom.of("chat-widget").prop("session", "s1"))
                .edge(UiDrawer.Edge.BOTTOM).size("45%").minSize("200px").maxSize("80%").resizable()
                .scope(UiDrawer.Scope.CONTAINER).icon("sparkles").badge("2")
                .onClose(UiTrigger.api("DELETE", "/chat/s1"))
                .onStateChange(UiTrigger.api("POST", "/chat/s1/state/{state}"));
        JsonNode json = mapper.valueToTree(drawer);
        assertEquals("drawer", json.get("type").asText());
        assertEquals("Draft with AI", json.get("title").asText());
        assertEquals("chat-widget", json.get("content").get("type").asText());
        assertEquals("BOTTOM", json.get("edge").asText());
        assertEquals("CONTAINER", json.get("scope").asText());
        assertEquals("45%", json.get("size").asText());
        assertTrue(json.get("resizable").asBoolean());
        assertTrue(json.get("closable").asBoolean(), "onClose implies the X");
        assertEquals("/chat/s1/state/{state}", json.get("onStateChange").get("url").asText());

        UiDrawer back = assertInstanceOf(UiDrawer.class, mapper.treeToValue(json, UiNode.class));
        assertEquals(UiDrawer.Edge.BOTTOM, back.getEdge());
        assertEquals("s1", ((UiCustom) back.getContent()).prop("session"));
        assertEquals(json, mapper.valueToTree(back), "lossless");
    }

    @Test
    void whatIsNotSetStaysOffTheWire() throws Exception {
        JsonNode json = mapper.valueToTree(UiDrawer.of("d", "D", null));
        // No state: "as it is" — a patch keeps what the user chose.
        for (String absent : new String[]{"state", "edge", "scope", "mode", "size", "resizable", "closable", "onClose", "content"}) {
            assertFalse(json.has(absent), absent + " in " + json);
        }
        assertNull(UiDrawer.of("d", "D", null).getState());
        assertEquals(UiDrawer.State.MINIMIZED, UiDrawer.of("d", "D", null).minimized().getState());
    }

    @Test
    void onlyCssLengthsAreSizes() {
        for (String bad : new String[]{"40", "red", "calc(1px)", "1px;background:url(x)", "40%\" onclick=\"x", ""}) {
            assertThrows(IllegalArgumentException.class, () -> UiDrawer.of("d", "D", null).size(bad), bad);
            assertThrows(IllegalArgumentException.class, () -> UiDrawer.of("d", "D", null).minSize(bad), bad);
            assertThrows(IllegalArgumentException.class, () -> UiDrawer.of("d", "D", null).maxSize(bad), bad);
        }
        assertThrows(Exception.class, () -> mapper.readValue("{\"type\":\"drawer\",\"id\":\"d\",\"size\":\"1px;x:y\"}", UiNode.class),
                "the check holds for JSON read back too");
        assertEquals("360px", UiDrawer.of("d", "D", null).size("360px").getSize());
    }
}
