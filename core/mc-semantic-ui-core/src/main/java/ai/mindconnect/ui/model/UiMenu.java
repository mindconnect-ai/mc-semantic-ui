package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertical navigation menu — the collapsible sidebar of an admin shell.
 * Holds nestable {@link UiMenuItem}s and toggles between three states via a
 * hamburger control:
 *
 * <ul>
 *   <li>{@link State#EXPANDED} — full width, icon + label, groups expand inline.</li>
 *   <li>{@link State#RAIL} — a narrow icon-only rail; groups appear as hover fly-outs.</li>
 *   <li>{@link State#HIDDEN} — off-canvas; only the hamburger shows.</li>
 * </ul>
 *
 * <p>The {@link #state} field is the <em>initial</em> state the server renders.
 * With the SPA event bus loaded, the hamburger cycles the state client-side and
 * persists the choice (localStorage), so it survives re-renders without a
 * server round-trip; a server can still drive it explicitly by patching this
 * node with a different {@code state}. With no JS at all, items are real links
 * (navigable), groups are native {@code <details>} disclosures, and a
 * checkbox-hack hamburger still shows/hides the list.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiMenu extends UiNode {

    public enum State { EXPANDED, RAIL, HIDDEN }

    /**
     * How the sidebar relates to the content beside it:
     * <ul>
     *   <li>{@link Mode#PUSH} (default) — the menu occupies layout space; as it
     *       collapses to a rail or hides, sibling content reflows wider.</li>
     *   <li>{@link Mode#OVERLAY} — the menu floats over the content as a drawer
     *       (with a backdrop); the content does not move. Pair it with a
     *       hamburger in the header, since {@code HIDDEN} slides it off-canvas.</li>
     *   <li>{@link Mode#RESPONSIVE} — push on a wide screen (the hamburger
     *       toggles expanded ⇄ rail), an overlay drawer on a narrow one (closed
     *       by default; the hamburger opens it, the backdrop closes it). This is
     *       the usual admin-shell behaviour: "collapse to a rail on desktop,
     *       disappear behind a burger on mobile".</li>
     * </ul>
     * Placement is the app's job either way — drop the menu next to the content
     * in a horizontal {@code UiStack}. Overlay / responsive need that container
     * to be a positioning context (CSS {@code position: relative}).
     */
    public enum Mode { PUSH, OVERLAY, RESPONSIVE }

    /**
     * Which edge the menu sits on. Controls the visual orientation — the border
     * edge, the direction rail fly-outs / tooltips open, and the direction an
     * overlay drawer slides off-canvas. For a {@link Mode#PUSH} menu also place
     * it on the matching side of the content in your {@code UiStack} (before the
     * content for {@link Side#LEFT}, after it for {@link Side#RIGHT}).
     */
    public enum Side { LEFT, RIGHT }

    /** Top-level entries. */
    private List<UiMenuItem> items;

    /** Initial display state. Defaults to {@link State#EXPANDED} when null. */
    private State state;

    /** Push (default) or overlay/drawer. Defaults to {@link Mode#PUSH} when null. */
    private Mode mode;

    /** Which edge the menu sits on. Defaults to {@link Side#LEFT} when null. */
    private Side side;

    /** Whether to render the hamburger toggle. Defaults to true when null. */
    private Boolean toggle;

    public static UiMenu of(UiMenuItem... items) {
        var m = new UiMenu();
        m.items = new ArrayList<>(List.of(items));
        return m;
    }

    public static UiMenu of(String id, String title, UiMenuItem... items) {
        var m = of(items);
        m.setId(id);
        m.setTitle(title);
        return m;
    }

    public UiMenu state(State state) {
        this.state = state;
        return this;
    }

    public UiMenu mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public UiMenu side(Side side) {
        this.side = side;
        return this;
    }

    public UiMenu toggle(boolean toggle) {
        this.toggle = toggle;
        return this;
    }

    public UiMenu item(UiMenuItem item) {
        if (this.items == null) this.items = new ArrayList<>();
        this.items.add(item);
        return this;
    }
}
