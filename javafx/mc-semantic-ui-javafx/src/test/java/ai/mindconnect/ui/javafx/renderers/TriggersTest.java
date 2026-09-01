package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.model.UiRow;
import ai.mindconnect.ui.model.UiTrigger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trigger templates are written once and used many times — one per table for
 * row actions, one per pager for pages — so each rendered control has to
 * resolve the template's placeholder before firing it.
 *
 * <p>Found by pointing the client at a live admin UI: the tools table sent its
 * requests to a path with {@code {id}} still in it and the pager to one with
 * {@code {page}}, so View, Edit, Delete and both pager buttons did nothing.
 */
class TriggersTest {

    private static UiRow row(String id, Map<String, Object> data) {
        var row = new UiRow();
        row.setId(id);
        if (data != null) row.setData(new java.util.LinkedHashMap<>(data));
        return row;
    }

    private static UiTrigger template(String url) {
        var t = UiTrigger.api("DELETE", url, null);
        return t;
    }

    @Test
    void thePlaceholderBecomesTheRowsOwnId() {
        var resolved = Triggers.forRow(
                template("/admin/api/agents/7/tools/{id}"), row("tool-42", null));

        assertThat(resolved.getUrl()).isEqualTo("/admin/api/agents/7/tools/tool-42");
        // Everything else about the trigger is carried over untouched.
        assertThat(resolved.getMethod()).isEqualTo("DELETE");
    }

    @Test
    void anIdLivingInTheRowDataAlsoCounts() {
        // Whether the id was promoted onto the row or left in its data map
        // depends on how the server built the table; both have to work.
        var resolved = Triggers.forRow(
                template("/x/{id}"), row(null, Map.of("id", "from-data")));

        assertThat(resolved.getUrl()).isEqualTo("/x/from-data");
    }

    @Test
    void theTemplateIsLeftAloneWhenItCarriesNoPlaceholder() {
        var original = template("/admin/api/tools");

        // Same instance: nothing to substitute, nothing to copy.
        assertThat(Triggers.forRow(original, row("7", null))).isSameAs(original);
    }

    @Test
    void aRowWithNoIdAtAllFiresTheTemplateUnchanged() {
        var original = template("/x/{id}");

        // Better to send the template and let the server answer than to
        // silently drop the click.
        assertThat(Triggers.forRow(original, row(null, Map.of()))).isSameAs(original);
        assertThat(Triggers.forRow(null, row("7", null))).isNull();
    }

    @Test
    void thePagerPutsItsTargetPageInTheUrl() {
        var template = UiTrigger.api("GET", "/admin/agents?page={page}", null);

        assertThat(Triggers.forPage(template, 2).getUrl()).isEqualTo("/admin/agents?page=2");
        // Page 0 is a real page, not an absent value.
        assertThat(Triggers.forPage(template, 0).getUrl()).isEqualTo("/admin/agents?page=0");
    }

    @Test
    void aPageTriggerWithoutThePlaceholderIsFiredAsModelled() {
        var original = UiTrigger.api("GET", "/admin/agents", null);

        assertThat(Triggers.forPage(original, 3)).isSameAs(original);
        assertThat(Triggers.forPage(null, 3)).isNull();
    }
}
