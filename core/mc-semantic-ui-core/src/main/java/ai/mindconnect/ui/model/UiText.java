package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A bare text node. Used most often inside a {@link UiColumn#getCellTemplate()}
 * to render row data ({@code "text": "{sku}"} substitutes per row), but
 * works anywhere a string of text needs to participate in the UiNode tree —
 * for example a label inside a {@link UiStack}.
 *
 * <p>Renders as {@code <span class="sui-text" id="...">…</span>} so the
 * editor's id-based selection still finds it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiText extends UiNode {

    /**
     * The text to display. Can contain {@code {dataKey}} placeholders when
     * used as a {@link UiColumn#getCellTemplate()} — the table renderer
     * substitutes those against the current row's data map per cell.
     */
    private String text;

    public static UiText of(String text) {
        var t = new UiText();
        t.text = text;
        return t;
    }

    public static UiText of(String id, String text) {
        var t = new UiText();
        t.setId(id);
        t.text = text;
        return t;
    }
}
