package ai.mindconnect.ui.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class UiSection extends UiNode {

    /**
     * @deprecated use {@link UiSectionEntry} directly. Thin alias kept so
     *             call-sites that referenced {@code UiSection.Entry.of(...)}
     *             keep compiling during migration.
     */
    @Deprecated
    public static final class Entry {
        public static UiSectionEntry of(String id, String title, UiNode content) {
            return UiSectionEntry.of(id, title, content);
        }
    }

    /**
     * How the tab bar copes when the tabs don't fit the width:
     * <ul>
     *   <li>{@link TabOverflow#WRAP} (default) — tabs flow onto more rows.</li>
     *   <li>{@link TabOverflow#MENU} — the bar stays a single row and the tabs
     *       that don't fit collapse into a trailing "⋯ More" dropdown (needs the
     *       SPA; with no JS it falls back to wrapping).</li>
     * </ul>
     */
    public enum TabOverflow { WRAP, MENU }

    private List<UiSectionEntry> sections     = new ArrayList<>();
    private String               initialSection;
    /** Tab-bar overflow behaviour. Defaults to {@link TabOverflow#WRAP}. */
    private TabOverflow          tabOverflow;
    /**
     * When set, the section renders inside a {@code <details>} disclosure
     * widget. {@code collapseSummary} is the summary line shown when
     * collapsed; {@link #collapseOpen} controls the initial state.
     * Null/blank disables collapsing (default).
     */
    private String      collapseSummary;
    /** Initial open/closed state when {@link #collapseSummary} is set. */
    private boolean     collapseOpen;

    public UiSection section(String id, String title, UiNode content) {
        sections.add(UiSectionEntry.of(id, title, content));
        return this;
    }

    /**
     * Adds a section whose tab renders as a navigation link to
     * {@code href} in SSR mode. The {@code content} is still required so
     * the active panel (selected via {@link #initialSection(String)}) has
     * something to show on the destination page.
     */
    public UiSection section(String id, String title, String href, UiNode content) {
        sections.add(UiSectionEntry.of(id, title, href, content));
        return this;
    }

    public UiSection initialSection(String sectionId) {
        this.initialSection = sectionId;
        return this;
    }

    public UiSection tabOverflow(TabOverflow tabOverflow) {
        this.tabOverflow = tabOverflow;
        return this;
    }

    /**
     * Wraps this section's content in a {@code <details>} disclosure widget.
     * The renderer hides the section's body until the user clicks the
     * summary; pure native HTML, no JavaScript.
     */
    public UiSection collapsible(String summary, boolean open) {
        this.collapseSummary = summary;
        this.collapseOpen = open;
        return this;
    }

    public static UiSection of(String id, String title) {
        var s = new UiSection();
        s.setId(id); s.setTitle(title);
        return s;
    }
}
