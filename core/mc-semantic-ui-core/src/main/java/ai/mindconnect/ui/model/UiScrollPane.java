package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A scrolling viewport around one child — the element to reach for when a
 * feed or a long list should scroll <em>inside</em> the page instead of
 * growing it: chat threads, live run logs, master lists in a master-detail.
 *
 * <p>Height: {@link #maxHeight} caps the pane in normal flow; without it the
 * pane fills the leftover space of a flex-column parent (a chat page's
 * "messages take everything between header and composer" layout).
 *
 * <p>{@link #stickToLatest} turns the pane into a live feed: as long as the
 * user is at (or near) the bottom, new content keeps the view pinned to the
 * newest entry. Scrolling up to read stops the sticking and surfaces a
 * floating jump-to-latest arrow; clicking it (or scrolling back down)
 * re-arms it. Wired client-side by the bus's auto-enhance — a no-JS page
 * simply scrolls.
 *
 * <p>A {@link UiList} placed directly inside a pane keeps its header row in
 * view (sticky) while the items scroll — the list's title and header actions
 * behave like the pane's toolbar.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiScrollPane extends UiNode {

    /** The scrolled content. Any node; usually a {@link UiList} or {@link UiStack}. */
    private UiNode content;

    /**
     * CSS length capping the pane's height (e.g. {@code "60vh"},
     * {@code "400px"}). Null = fill the parent (flex-column layouts).
     */
    private String maxHeight;

    /** Live-feed mode: stick to the newest content + jump-to-latest arrow. */
    private Boolean stickToLatest;

    public static UiScrollPane of(String id, UiNode content) {
        var p = new UiScrollPane();
        p.setId(id);
        p.content = content;
        return p;
    }

    public UiScrollPane content(UiNode content)   { this.content = content; return this; }
    public UiScrollPane maxHeight(String height)  { this.maxHeight = height; return this; }
    public UiScrollPane stickToLatest(boolean b)  { this.stickToLatest = b; return this; }
}
