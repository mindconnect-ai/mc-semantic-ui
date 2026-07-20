package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiLink extends UiNode {

    /** Link relation / role (e.g. "back", "next"). Used historically as the link's id slot too. */
    private String rel;
    private String href;
    private String label;
    private boolean external;
    /** Leading icon token rendered before the label. See {@link UiIcon}. */
    private String icon;

    public static UiLink of(String rel, String href, String label) {
        var l = new UiLink();
        l.rel = rel; l.href = href; l.label = label;
        // Keep id == rel for backwards compatibility with renderer + patch ids.
        l.setId(rel);
        return l;
    }

    public static UiLink external(String rel, String href, String label) {
        var l = of(rel, href, label);
        l.external = true;
        return l;
    }

    /** Set the leading icon token (fluent). */
    public UiLink icon(String iconToken) {
        this.icon = iconToken;
        return this;
    }

    /** Attach a click behaviour dispatched via the event bus (fluent). */
    public UiLink onClick(UiTrigger trigger) {
        setOnClick(trigger);
        return this;
    }
}
