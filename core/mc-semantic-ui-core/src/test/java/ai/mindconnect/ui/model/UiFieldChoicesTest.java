package ai.mindconnect.ui.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The choice rules on the model that every Java renderer reads: which options
 * are checked, in what order, and where a row goes when it is ticked. The SPA
 * mirrors them in renderers/choices.ts, held to the same fixtures.
 */
class UiFieldChoicesTest {

    @Test
    void seatAfterToggleFollowsTheSharedFixtures() throws Exception {
        var fixtures = new ObjectMapper().readTree(Path.of("src/test/resources/fixtures/choice-seats.json").toFile());
        assertTrue(fixtures.size() > 0);
        for (var fixture : fixtures) {
            var checked = new ArrayList<Boolean>();
            fixture.get("checked").forEach(c -> checked.add(c.asBoolean()));
            var index = new ArrayList<Integer>();
            fixture.get("index").forEach(i -> index.add(i.asInt()));
            assertEquals(fixture.get("seat").asInt(),
                    UiField.seatAfterToggle(checked, index, fixture.get("at").asInt()),
                    fixture.get("name").asText());
        }
    }

    @Test
    void valueTextIsWhatJavaScriptWouldPrint() {
        assertEquals("2", UiField.valueText(2.0));
        assertEquals("1.5", UiField.valueText(1.5));
        assertEquals("2", UiField.valueText(new BigDecimal("2.00")));
        assertEquals("0", UiField.valueText(new BigDecimal("0.000")));
        assertEquals("a,,b", UiField.valueText(Arrays.asList("a", null, "b")));
        assertEquals("s,l", UiField.valueText(new String[]{"s", "l"}));
        assertEquals("true", UiField.valueText(true));
    }

    @Test
    void selectedValuesKeepEmptyPartsAndReadArrays() {
        assertEquals(List.of("a", ""), UiField.multiselect("f", "F", "a,", List.of()).selectedValues());
        assertEquals(List.of(), UiField.multiselect("f", "F", " \u00A0", List.of()).selectedValues());
        assertEquals(List.of("s", "l"), UiField.multiselect("f", "F", new String[]{"s", "l"}, List.of()).selectedValues());
        assertEquals(List.of("1", "2"), UiField.multiselect("f", "F", new int[]{1, 2}, List.of()).selectedValues());
    }

    @Test
    void nullOptionsAreSkippedAndKeepTheOthersIndexes() {
        var options = new ArrayList<UiField.Option>(Arrays.asList(null, UiField.Option.of("x", "X")));
        var choices = UiField.select("f", "F", "x", options).choicesInDisplayOrder();
        assertEquals(1, choices.size());
        assertEquals(1, choices.get(0).index());
        assertTrue(choices.get(0).checked());
    }
}
