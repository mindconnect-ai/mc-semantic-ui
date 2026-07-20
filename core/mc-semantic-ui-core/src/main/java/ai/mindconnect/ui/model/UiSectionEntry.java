package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One tab / panel inside a {@link UiSection}. Carries the tab's display
 * title, an optional SSR navigation {@code href}, and the inner
 * {@link UiNode} that becomes the panel's body.
 *
 * <p><b>Why a UiNode?</b> Until this lived as a plain {@code static class
 * Entry} the editor had to special-case it everywhere — a "wrapper" with no
 * type discriminator can't be selected, edited, or deleted with the same
 * code path as everything else. Making it a UiNode collapses three layers
 * of plumbing (entryContentProperty, renderEntryWrapper, wrapper-detection
 * heuristic in deleteAt) into "it's just a tree node".
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiSectionEntry extends UiNode {

    /** The body shown when this tab is active. */
    private UiNode content;
    /**
     * Optional. When set, the tab renders as an {@code <a href>} navigation
     * link instead of a JS-driven {@code <button>}. Used in SSR mode where
     * children live on separate URLs and a click should reload the page
     * rather than swap a panel client-side. Null = client-side panel switch.
     */
    private String href;
    /** Leading icon token shown before the tab title. See {@link UiIcon}. */
    private String icon;

    /**
     * Whether clicking this tab also switches to its panel. Defaults to true —
     * the obvious behaviour, and what every tab bar does.
     *
     * <p>Set it false when the application wants to decide: the click still
     * fires {@link UiNode#getOnClick()}, but the panel does not change. The
     * handler then selects a tab itself by returning a {@link UiPatch} that
     * replaces the section with a different {@code initialSection} — which is
     * how you gate a tab behind a confirmation, a permission check or a save.
     *
     * <p>Without this the trigger fires <em>after</em> the switch has already
     * happened, so it can react but never prevent.
     */
    private Boolean selectOnClick;

    public UiSectionEntry selectOnClick(boolean select) {
        this.selectOnClick = select;
        return this;
    }

    public static UiSectionEntry of(String id, String title, UiNode content) {
        var e = new UiSectionEntry();
        e.setId(id);
        e.setTitle(title);
        e.content = content;
        return e;
    }

    public static UiSectionEntry of(String id, String title, String href, UiNode content) {
        var e = of(id, title, content);
        e.href = href;
        return e;
    }

    /** Set the leading tab icon token (fluent). */
    public UiSectionEntry icon(String iconToken) {
        this.icon = iconToken;
        return this;
    }

    /** Set the tab's click trigger (fluent). Fires alongside the panel switch. */
    public UiSectionEntry onClick(UiTrigger trigger) {
        setOnClick(trigger);
        return this;
    }
}
