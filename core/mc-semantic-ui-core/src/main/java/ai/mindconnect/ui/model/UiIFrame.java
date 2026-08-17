package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * An embedded browsing context — the element for folding a whole foreign page
 * into a semantic-ui screen: a Swagger UI, a Grafana dashboard, a legacy
 * admin page. The framework renders the {@code <iframe>} and its sizing;
 * what happens inside is the embedded page's business.
 *
 * <p>Height: {@link #height} caps the frame at a CSS length in normal flow;
 * without it the frame fills the leftover space of a flex-column parent
 * (same convention as {@link UiScrollPane}) — the usual "the embedded app
 * takes the whole content pane" layout.
 *
 * <p>{@link UiNode#getTitle() title} becomes the iframe's {@code title}
 * attribute — set it: it is how screen readers name the embedded region.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiIFrame extends UiNode {

    /** The embedded page's URL. Same-origin or absolute. */
    private String src;

    /**
     * CSS length capping the frame's height (e.g. {@code "70vh"},
     * {@code "600px"}). Null = fill the parent (flex-column layouts).
     */
    private String height;

    /**
     * Optional {@code sandbox} attribute value (e.g.
     * {@code "allow-scripts allow-same-origin"}). Null renders no sandbox —
     * full trust, appropriate for same-origin embeds like a bundled
     * Swagger UI. Set it when embedding third-party content.
     */
    private String sandbox;

    public static UiIFrame of(String id, String src) {
        var f = new UiIFrame();
        f.setId(id);
        f.src = src;
        return f;
    }

    public UiIFrame src(String src)        { this.src = src;       return this; }
    public UiIFrame height(String height)  { this.height = height; return this; }
    public UiIFrame sandbox(String value)  { this.sandbox = value; return this; }
    public UiIFrame title(String title)    { setTitle(title);      return this; }
}
