package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server writes what the browser's suggestion enhancer looks for — the
 * same attributes, in the same order, as renderers/field.ts (suggest.test.mjs
 * checks that side).
 */
class FieldSuggestRenderTest {

    private final SuiServerRenderer renderer = new SuiServerRenderer();
    private static final String COMBOBOX = " autocomplete=\"off\" role=\"combobox\" aria-autocomplete=\"list\" aria-expanded=\"false\"";

    @Test
    void aRecipientLineCarriesUrlSeparatorMinimumAndPause() {
        String html = renderer.render(UiField.text("to", "To", "").asEditable()
                .suggest(UiTrigger.go("/contacts/suggest"))
                .suggestSeparator(",").suggestMinChars(2).suggestDebounceMs(150));
        assertTrue(html.contains("data-sui-type=\"TEXT\"" + COMBOBOX
                + " data-sui-suggest=\"/contacts/suggest\" data-sui-suggest-separator=\",\""
                + " data-sui-suggest-min=\"2\" data-sui-suggest-debounce=\"150\">"), html);
    }

    @Test
    void aTextareaToo_andTheDefaultsAreLeftToTheBrowser() {
        String html = renderer.render(UiField.textarea("note", "Note", "").asEditable().suggest(UiTrigger.go("/s")));
        assertTrue(html.contains("<textarea id=\"note__input\" name=\"note\" rows=\"4\" data-sui-type=\"TEXTAREA\"" + COMBOBOX
                + " data-sui-suggest=\"/s\">"), html);
        assertFalse(html.contains("data-sui-suggest-min"), html);
        assertFalse(html.contains("data-sui-suggest-mode"), html);
    }

    @Test
    void aSelectIsASearchBoxAndAHiddenValue() {
        String html = renderer.render(UiField.select("owner", "Owner", "u7", List.of(UiField.Option.of("u7", "Anna Muster")))
                .asEditable().suggest(UiTrigger.go("/people")));
        assertTrue(html.contains("<input type=\"text\" id=\"owner__input\" value=\"Anna Muster\" placeholder=\"\""
                + " data-sui-suggest-label=\"Anna Muster\"" + COMBOBOX
                + " data-sui-suggest=\"/people\" data-sui-suggest-mode=\"one\">"
                + "<input type=\"hidden\" name=\"owner\" value=\"u7\" data-sui-type=\"SELECT\">"), html);
        assertFalse(html.contains("<select"), html);
    }

    @Test
    void aMultiselectIsAChipPerPickAndASearchBox() {
        String html = renderer.render(UiField.multiselect("tags", "Tags", List.of("u7", "u9"),
                        List.of(UiField.Option.of("u7", "Anna"))).asEditable().suggest(UiTrigger.go("/tags")));
        assertTrue(html.contains("<div class=\"sui-chips\" data-sui-chips=\"tags\">"
                + "<span class=\"sui-chip\" data-value=\"u7\"><span class=\"sui-chip-label\">Anna</span>"
                + "<button type=\"button\" class=\"sui-chip-remove\" data-sui-chip-remove aria-label=\"Remove Anna\">"), html);
        assertTrue(html.contains("<input type=\"hidden\" name=\"tags\" value=\"u7\" data-sui-type=\"MULTISELECT\"></span>"), html);
        assertTrue(html.contains("<span class=\"sui-chip\" data-value=\"u9\"><span class=\"sui-chip-label\">u9</span>"), html);
        assertTrue(html.contains("<input type=\"text\" id=\"tags__input\" class=\"sui-chips-input\" placeholder=\"\"" + COMBOBOX
                + " data-sui-suggest=\"/tags\" data-sui-suggest-mode=\"many\"></div>"), html);
        assertEquals(2, html.split("data-sui-type=\"MULTISELECT\"", -1).length - 1, html);
    }

    @Test
    void theChangeMarkersSitOnTheSearchBox() {
        String html = renderer.render(UiField.select("owner", "Owner", null, List.of()).asEditable()
                .suggest(UiTrigger.go("/people")).onChange(UiTrigger.api("POST", "/owner")));
        int box = html.indexOf("id=\"owner__input\"");
        int marker = html.indexOf("data-change-trigger=");
        int hidden = html.indexOf("type=\"hidden\"");
        assertTrue(box >= 0 && marker > box && marker < hidden, html);
    }

    @Test
    void noSuggestionsWhereTheyDoNotApply() {
        var go = UiTrigger.go("/s");
        for (UiField f : List.of(
                UiField.select("a", "A", null, List.of(UiField.Option.of("x", "X"))).asEditable().asRadio().suggest(go),
                UiField.text("b", "B", "x").suggest(go),
                UiField.number("c", "C", 1).asEditable().suggest(go),
                UiField.text("d", "D", null).asEditable())) {
            assertFalse(renderer.render(f).contains("data-sui-suggest"), f.getId());
        }
    }
}
