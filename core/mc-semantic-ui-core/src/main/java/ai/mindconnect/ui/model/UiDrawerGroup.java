package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Several {@link UiDrawer}s at one edge, and how they get on with each other.
 *
 * <p>Two drawers at the same edge would otherwise lie over one another. A
 * group says what happens instead — as configuration, not as a rule the
 * renderer guesses:
 *
 * <ul>
 *   <li>{@link Arrange#SHARE} — the open ones divide the edge between them.
 *       Two at the bottom stand side by side, each half the width, the full
 *       height of the strip. A minimized one drops to its handle and the
 *       others take its room.</li>
 *   <li>{@link Arrange#STACK} — they lie on top of each other. The one in
 *       front ({@link #active}) shows its content; the others show only their
 *       header bars, stacked above it like cards, and a click on a bar brings
 *       that drawer to the front. The change is reported through
 *       {@link #onActiveChange}, with {@code {id}} in the URL.</li>
 * </ul>
 *
 * <pre>{@code
 * UiDrawerGroup.of("dock", chat, files)
 *     .edge(UiDrawer.Edge.BOTTOM).scope(UiDrawer.Scope.CONTAINER)
 *     .arrange(UiDrawerGroup.Arrange.SHARE)
 *     .size("40%").minSize("160px").resizable();
 * }</pre>
 *
 * <h2>What the group owns</h2>
 * The edge, the scope and the mode are the group's — a drawer inside one
 * ignores its own, so there is one truth about where it stands. So is the
 * size: it is the strip's extent along the edge's axis (the height of a
 * bottom strip), and the drawers share the other axis. What a drawer keeps:
 * its title, icon and badge, its content, {@link UiDrawer#isClosable() closable}
 * and {@link UiDrawer#getOnClose() onClose}, and its own state — open,
 * minimized, closed — with the same rules as outside a group.
 *
 * <p>Rendered by the browser today. The server-side template and the JavaFX
 * painter are still to come; until then the server renders a group as a
 * placeholder and the desktop client shows nothing for it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiDrawerGroup extends UiNode {

    /** How the open drawers of a group divide the edge — or do not. */
    public enum Arrange {
        /** Side by side along the edge, equal parts. */
        SHARE,
        /** On top of each other; the front one shows, the others peek with their header. */
        STACK
    }

    /** The drawers, in the order they stand along the edge (SHARE) or were stacked (STACK). */
    private List<UiDrawer> drawers = new ArrayList<>();
    /** Where the group slides in from. Default {@link UiDrawer.Edge#RIGHT}. */
    private UiDrawer.Edge edge;
    /** Default {@link UiDrawer.Scope#VIEWPORT}. */
    private UiDrawer.Scope scope;
    /** Default {@link UiDrawer.Mode#OVERLAY}. */
    private UiDrawer.Mode mode;
    /** Default {@link Arrange#SHARE}. */
    private Arrange arrange;
    /** The strip's extent along the edge's axis — a CSS length. */
    private String size;
    private String minSize;
    private String maxSize;
    /** The strip's inner edge can be dragged. */
    private boolean resizable;
    /**
     * STACK: the id of the drawer in front. Null means "as it is" — on a
     * patch, whatever the user brought forward stays there; on a first render,
     * the first open drawer.
     */
    private String active;
    /**
     * Fired when the user brings another drawer to the front; {@code {id}} in
     * the URL becomes that drawer's id.
     */
    private UiTrigger onActiveChange;

    /** A group holding {@code drawers}, at the right edge until told otherwise. */
    public static UiDrawerGroup of(String id, UiDrawer... drawers) {
        UiDrawerGroup g = new UiDrawerGroup();
        g.setId(id);
        g.drawers = new ArrayList<>(List.of(drawers));
        return g;
    }

    public UiDrawerGroup drawer(UiDrawer drawer) {
        if (drawers == null) drawers = new ArrayList<>();
        drawers.add(drawer);
        return this;
    }

    public UiDrawerGroup edge(UiDrawer.Edge edge) { this.edge = edge; return this; }

    public UiDrawerGroup scope(UiDrawer.Scope scope) { this.scope = scope; return this; }

    public UiDrawerGroup mode(UiDrawer.Mode mode) { this.mode = mode; return this; }

    public UiDrawerGroup arrange(Arrange arrange) { this.arrange = arrange; return this; }

    /** The open drawers divide the edge between them. */
    public UiDrawerGroup share() { return arrange(Arrange.SHARE); }

    /** The drawers lie on top of each other; the front one shows. */
    public UiDrawerGroup stack() { return arrange(Arrange.STACK); }

    /** @throws IllegalArgumentException unless a CSS length such as {@code "40%"} */
    public UiDrawerGroup size(String size) { setSize(size); return this; }

    /** @throws IllegalArgumentException unless a CSS length */
    public UiDrawerGroup minSize(String size) { setMinSize(size); return this; }

    /** @throws IllegalArgumentException unless a CSS length */
    public UiDrawerGroup maxSize(String size) { setMaxSize(size); return this; }

    public UiDrawerGroup resizable() { this.resizable = true; return this; }

    /** STACK: which drawer is in front. */
    public UiDrawerGroup active(String drawerId) { this.active = drawerId; return this; }

    public UiDrawerGroup onActiveChange(UiTrigger trigger) { this.onActiveChange = trigger; return this; }

    public void setSize(String size) { this.size = length("size", size); }

    public void setMinSize(String size) { this.minSize = length("minSize", size); }

    public void setMaxSize(String size) { this.maxSize = length("maxSize", size); }

    /** The sizes go into a style attribute: a number and a unit, nothing else. */
    private static String length(String what, String value) {
        if (value == null) return null;
        if (!UiDrawer.CSS_LENGTH.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a CSS length such as \"40%\" or \"320px\", not \"" + value + "\"");
        }
        return value;
    }
}
