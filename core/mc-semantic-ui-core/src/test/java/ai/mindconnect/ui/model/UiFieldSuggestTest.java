package ai.mindconnect.ui.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UiField.suggest on the wire and in the rule the renderers share: which
 * field takes suggestions how, and what a SELECT or MULTISELECT shows as
 * picked.
 */
class UiFieldSuggestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static UiField recipients() {
        return UiField.text("to", "To", "anna@example.com, ").asEditable()
                .suggest(UiTrigger.go("/contacts/suggest"))
                .suggestSeparator(",").suggestMinChars(2).suggestDebounceMs(150);
    }

    @Test
    void theSuggestOptionsTravelAndComeBack() throws Exception {
        String json = mapper.writeValueAsString(recipients());
        assertTrue(json.contains("\"suggest\":{"), json);
        assertTrue(json.contains("\"url\":\"/contacts/suggest\""), json);
        assertTrue(json.contains("\"suggestSeparator\":\",\""), json);
        assertTrue(json.contains("\"suggestMinChars\":2"), json);
        assertTrue(json.contains("\"suggestDebounceMs\":150"), json);
        // The derived rules are for the renderers, not the wire.
        assertFalse(json.contains("suggestMode"), json);
        assertFalse(json.contains("suggestPicks"), json);

        UiField back = (UiField) mapper.readValue(json, UiNode.class);
        assertEquals("/contacts/suggest", back.getSuggest().getUrl());
        assertEquals(",", back.getSuggestSeparator());
        assertEquals(2, back.getSuggestMinChars());
        assertEquals(150, back.getSuggestDebounceMs());
    }

    @Test
    void unsetDefaultsStayOffTheWire() throws Exception {
        var plain = UiField.text("q", "Search", null).asEditable().suggest(UiTrigger.go("/s"));
        String json = mapper.writeValueAsString(plain);
        assertFalse(json.contains("suggestSeparator"), json);
        assertFalse(json.contains("suggestMinChars"), json);
        assertFalse(json.contains("suggestDebounceMs"), json);
        assertFalse(mapper.writeValueAsString(UiField.text("n", "Name", null)).contains("suggest"));
    }

    @Test
    void theModeFollowsTheFieldType() {
        var go = UiTrigger.go("/s");
        assertEquals("text", UiField.text("a", "A", null).asEditable().suggest(go).getSuggestMode());
        assertEquals("text", UiField.textarea("a", "A", null).asEditable().suggest(go).getSuggestMode());
        assertEquals("one", UiField.select("a", "A", null, List.of()).asEditable().suggest(go).getSuggestMode());
        assertEquals("many", UiField.multiselect("a", "A", null, List.of()).asEditable().suggest(go).getSuggestMode());
        // Not where it cannot apply.
        assertNull(UiField.select("a", "A", null, List.of()).asEditable().asRadio().suggest(go).getSuggestMode());
        assertNull(UiField.number("a", "A", null).asEditable().suggest(go).getSuggestMode());
        assertNull(UiField.text("a", "A", null).suggest(go).getSuggestMode());
        assertNull(UiField.text("a", "A", null).asEditable().suggest(UiTrigger.go(" ")).getSuggestMode());
        assertNull(UiField.text("a", "A", null).asEditable().getSuggestMode());
    }

    @Test
    void picksCarryTheLabelTheOptionsKnowOrTheValue() {
        var options = List.of(UiField.Option.of("u7", "Anna Muster"));
        var many = UiField.multiselect("to", "To", List.of("u7", "u9"), options).asEditable().suggest(UiTrigger.go("/s"));
        assertEquals(List.of("u7", "u9"), many.getSuggestPicks().stream().map(UiField.Option::getValue).toList());
        assertEquals(List.of("Anna Muster", "u9"), many.getSuggestPicks().stream().map(UiField.Option::getLabel).toList());

        var one = UiField.select("owner", "Owner", "u7", options).asEditable().suggest(UiTrigger.go("/s"));
        assertEquals("Anna Muster", one.getSuggestPicks().get(0).getLabel());
        assertTrue(UiField.select("owner", "Owner", null, options).getSuggestPicks().isEmpty());
    }

    @Test
    void negativeNumbersAreRefused() {
        var f = UiField.text("a", "A", null);
        assertThrows(IllegalArgumentException.class, () -> f.suggestMinChars(-1));
        assertThrows(IllegalArgumentException.class, () -> f.suggestDebounceMs(-5));
    }
}
