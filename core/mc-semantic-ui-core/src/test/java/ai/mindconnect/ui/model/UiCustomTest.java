package ai.mindconnect.ui.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A node whose type is set by hand: flat on the wire, and read back from any type Jackson does not know. */
class UiCustomTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesFlatWithItsOwnType() throws Exception {
        String json = mapper.writeValueAsString(UiCustom.of("x-demo").prop("a", 1));
        assertEquals("{\"type\":\"x-demo\",\"a\":1}", json);

        UiNode asNode = UiCustom.of("chat-widget").id("draft-chat")
                .prop("session", "s1").prop("api", "/chat-api/sessions");
        assertEquals("{\"type\":\"chat-widget\",\"id\":\"draft-chat\",\"session\":\"s1\",\"api\":\"/chat-api/sessions\"}",
                mapper.writeValueAsString(asNode));
    }

    @Test
    void theStandardFieldsWorkAsOnEveryNode() throws Exception {
        UiCustom node = UiCustom.of("x-demo").id("d").title("Demo").cssClass("wide")
                .onClick(UiTrigger.api("GET", "/x")).prop("a", 1);
        node.hidden();
        JsonNode tree = mapper.readTree(mapper.writeValueAsString(node));
        assertEquals("x-demo", tree.get("type").asText());
        assertEquals("d", tree.get("id").asText());
        assertEquals("Demo", tree.get("title").asText());
        assertEquals("wide sui-hidden", tree.get("cssClass").asText());
        assertEquals("/x", tree.get("onClick").get("url").asText());
        assertEquals("HIDDEN", tree.get("display").asText());
        assertEquals(1, tree.get("a").asInt());
    }

    @Test
    void insideAContainerAndReadBack() throws Exception {
        UiStack stack = UiStack.of(UiText.of("t", "hi"), UiCustom.of("w", "x-demo").prop("a", 1).prop("list", List.of("p", "q")));
        stack.setId("s");
        String json = mapper.writeValueAsString(stack);
        assertTrue(json.contains("{\"type\":\"x-demo\",\"id\":\"w\",\"a\":1,\"list\":[\"p\",\"q\"]}"), json);

        UiStack back = (UiStack) mapper.readValue(json, UiNode.class);
        assertInstanceOf(UiText.class, back.getChildren().get(0), "known types read as before");
        UiCustom custom = assertInstanceOf(UiCustom.class, back.getChildren().get(1));
        assertEquals("x-demo", custom.getType());
        assertEquals("w", custom.getId());
        assertEquals(Map.of("a", 1, "list", List.of("p", "q")), custom.getProps());
        assertEquals(json, mapper.writeValueAsString(back), "the round trip is lossless");
    }

    @Test
    void anUnknownTypeIsReadAsACustomNode() throws Exception {
        UiNode node = mapper.readValue("{\"type\":\"x-demo\",\"id\":\"d\",\"cssClass\":\"c\",\"a\":1,\"nested\":{\"b\":true}}", UiNode.class);
        UiCustom custom = assertInstanceOf(UiCustom.class, node);
        assertEquals("x-demo", custom.getType());
        assertEquals("c", custom.getCssClass());
        assertEquals(Map.of("b", true), custom.prop("nested"));
    }

    @Test
    void aKnownTypeStaysExactlyAsItWas() throws Exception {
        String json = "{\"type\":\"text\",\"id\":\"t\",\"text\":\"hi\"}";
        UiText text = assertInstanceOf(UiText.class, mapper.readValue(json, UiNode.class));
        assertEquals("hi", text.getText());
        assertEquals(json, mapper.writeValueAsString(text));
        // An unknown field on a known type is still refused, as it always was.
        assertThrows(Exception.class, () -> mapper.readValue("{\"type\":\"text\",\"id\":\"t\",\"bogus\":1}", UiNode.class));
    }

    @Test
    void aCoreTypeOrAMalformedNameIsRejected() {
        for (String bad : new String[]{"list", "text", "section", "page", "Chat", "chat_widget", "-x", "x-", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> UiCustom.of(bad), String.valueOf(bad));
        }
        UiCustom.of("chat-widget");
        UiCustom.of("x2-demo");
        assertThrows(Exception.class, () -> mapper.readValue("{\"type\":\"list\",\"bogus\":1}", UiNode.class),
                "a core type is never read as a custom node");
    }

    @Test
    void aPropertyCannotOverwriteAStandardField() {
        for (String name : new String[]{"type", "id", "title", "cssClass", "onClick", "display", ""}) {
            assertThrows(IllegalArgumentException.class, () -> UiCustom.of("x-demo").prop(name, "v"), name);
        }
        assertEquals(null, UiCustom.of("x-demo").prop("a", 1).prop("a", null).prop("a"), "null removes");
    }
}
