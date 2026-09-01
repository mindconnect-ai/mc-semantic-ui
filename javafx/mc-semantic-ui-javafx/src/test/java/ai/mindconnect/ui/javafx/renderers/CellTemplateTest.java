package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiRow;
import ai.mindconnect.ui.model.UiText;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cell template is one node written once for a whole column, with
 * {@code {dataKey}} placeholders standing in for the row.
 *
 * <p>Found by pointing the JavaFX client at a live admin UI: its workflow list
 * is a table whose first column is a link to the workflow. Templates were not
 * applied, so the cell fell back to the raw value — the name showed as text and
 * there was no way to open anything.
 */
class CellTemplateTest {

    /** Built by hand: UiLink.of takes (rel, href, label), which is easy to mix up. */
    private static UiLink link(String id, String href, String label) {
        var l = new UiLink();
        l.setId(id);
        l.setHref(href);
        l.setLabel(label);
        return l;
    }

    private static UiRow row(String id, Map<String, Object> data) {
        var r = new UiRow();
        r.setId(id);
        r.setData(new LinkedHashMap<>(data));
        return r;
    }

    @Test
    void placeholdersAreFilledFromTheRow() {
        var template = link("wf-open", "/workflows/{id}", "{name}");

        var link = (UiLink) TableRenderer.forRow(template,
                row("approval", Map.of("name", "Approval", "steps", 3)));

        assertThat(link.getHref()).isEqualTo("/workflows/approval");
        assertThat(link.getLabel()).isEqualTo("Approval");
    }

    @Test
    void theRowsIdIsAvailableEvenWhenItLivesOnTheRowRatherThanTheData() {
        var template = link("open", "/workflows/{id}", "x");

        // Which of the two carries it depends on how the server built the table.
        assertThat(((UiLink) TableRenderer.forRow(template, row("on-the-row", Map.of()))).getHref())
                .isEqualTo("/workflows/on-the-row");
        assertThat(((UiLink) TableRenderer.forRow(template, row(null, Map.of("id", "in-the-data")))).getHref())
                .isEqualTo("/workflows/in-the-data");
    }

    @Test
    void idsAreSuffixedPerRowRatherThanSubstituted() {
        var template = link("wf-open", "/x", "x");

        var link = (UiLink) TableRenderer.forRow(template, row("approval", Map.of()));

        // The same node is painted once per row; two of them under one id would
        // collide in the render index, and a patch would find the wrong cell.
        assertThat(link.getId()).isEqualTo("wf-open__approval");
    }

    @Test
    void anUnknownPlaceholderIsLeftAsWritten() {
        var template = UiText.of("t", "{name} has {nosuchkey}");

        var text = (UiText) TableRenderer.forRow(template, row("r", Map.of("name", "Approval")));

        // Blanking it would hide the mistake; leaving it shows the author what
        // they typed. Same call the SPA makes.
        assertThat(text.getText()).isEqualTo("Approval has {nosuchkey}");
    }

    @Test
    void aNullValueBecomesEmptyRatherThanTheWordNull() {
        var data = new LinkedHashMap<String, Object>();
        data.put("name", null);
        var template = UiText.of("t", "[{name}]");

        var text = (UiText) TableRenderer.forRow(template, row("r", data));

        assertThat(text.getText()).isEqualTo("[]");
    }

    @Test
    void aTemplateIsClonedRatherThanMutated() {
        var template = link("wf-open", "/workflows/{id}", "{name}");

        TableRenderer.forRow(template, row("approval", Map.of("name", "Approval")));
        TableRenderer.forRow(template, row("classify", Map.of("name", "Classify")));

        // One template serves every row, so filling it in for one must not
        // spend it for the next.
        assertThat(template.getHref()).isEqualTo("/workflows/{id}");
        assertThat(template.getId()).isEqualTo("wf-open");
    }
}
