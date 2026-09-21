package ai.mindconnect.ui.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** editorHeight and fill on a RICHTEXT field: on the wire, and what is refused. */
class UiFieldRichTextHeightTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void editorHeightAndFillGoOverTheWire() throws Exception {
        JsonNode json = mapper.valueToTree(UiField.richtext("n", "Notes", "").asEditable().editorHeight("320px").fill());
        assertEquals("320px", json.get("editorHeight").asText());
        assertTrue(json.get("fill").asBoolean());

        UiField back = (UiField) mapper.treeToValue(json, UiNode.class);
        assertEquals("320px", back.getEditorHeight());
        assertTrue(back.isFill());
    }

    @Test
    void neitherIsWrittenWhenNotSet() throws Exception {
        JsonNode json = mapper.valueToTree(UiField.richtext("n", "Notes", ""));
        assertFalse(json.has("editorHeight"));
        assertFalse(json.has("fill"), "fill=false stays off the wire: " + json);
        assertNull(UiField.text("t", "T", "").getEditorHeight());
    }

    @Test
    void onlyACssLengthIsAHeight() {
        for (String ok : new String[]{"320px", "20rem", "1.5em", "40vh", "60dvh", "100%"}) {
            assertEquals(ok, UiField.richtext("n", "N", "").editorHeight(ok).getEditorHeight());
        }
        for (String bad : new String[]{"320", "red", "calc(100% - 2px)", "1px;background:url(x)", "320px\" onmouseover=\"x", "-5px", ""}) {
            assertThrows(IllegalArgumentException.class, () -> UiField.richtext("n", "N", "").editorHeight(bad), bad);
        }
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"type\":\"field\",\"id\":\"n\",\"fieldType\":\"RICHTEXT\",\"editorHeight\":\"1px;color:red\"}", UiNode.class),
                "the check holds for JSON read back too");
        UiField cleared = UiField.richtext("n", "N", "").editorHeight("320px");
        cleared.setEditorHeight(null);
        assertNull(cleared.getEditorHeight());
    }
}
