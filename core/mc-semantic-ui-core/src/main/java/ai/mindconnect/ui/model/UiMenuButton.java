package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * A button that opens a floating menu (a dropdown / context / "kebab" menu) of
 * {@link UiMenuItem}s anchored to itself. Unlike {@link UiMenu} — the persistent
 * sidebar — this is a transient popover: it lives closed, opens on click, and
 * closes on outside-click / Escape / after an item is chosen.
 *
 * <p>It is deliberately placeable <em>anywhere</em> a node can go: on its own as
 * a toolbar overflow ("⋮"), or dropped into another node to act as that node's
 * context menu — e.g. as a {@link UiTreeNode}'s {@code labelNode} so each tree
 * row carries its own actions:
 * <pre>{@code
 * UiTreeNode.of("f1", null).labelNode(UiStack.of(
 *     UiText.of("report.pdf"),
 *     UiMenuButton.of(
 *         UiMenuItem.of("open", "Open").icon("eye").onClick(...),
 *         UiMenuItem.of("ren",  "Rename").icon("edit").onClick(...),
 *         UiMenuItem.divider(),
 *         UiMenuItem.of("del",  "Delete").icon("delete").danger(true).onClick(...)
 *     )).direction(UiStack.Direction.HORIZONTAL));
 * }</pre>
 *
 * <p>The popover is rendered inline (so it works in SSR and stays inside the
 * event bus's scope) but positioned with {@code position: fixed} at open time,
 * so it is never clipped by a scrolling/overflow-hidden ancestor. Each item
 * reuses {@link UiMenuItem}: a leaf dispatches its {@code onClick} through the
 * bus (or navigates via {@code href} with no JS). With no JS at all the popover
 * degrades to a native {@code <details>} disclosure.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiMenuButton extends UiNode {

    /** How the trigger control looks. */
    public enum Variant {
        /** A square icon-only button (the default when no {@link #label} is set). */
        ICON,
        /** A normal labelled button (icon optional). */
        BUTTON
    }

    /** Which edge of the trigger the popover lines up with. */
    public enum Align {
        /** Left edge (menu opens rightwards). */
        START,
        /** Right edge (menu opens leftwards) — the default for a trailing "⋮". */
        END
    }

    /** The menu entries. Reuses {@link UiMenuItem} (label · icon · onClick · danger · divider). */
    private List<UiMenuItem> items;

    /** Trigger glyph token. Defaults to {@code "more"} (a vertical "⋮") when null. */
    private String icon;

    /** Optional trigger text; when set the trigger renders as a labelled button. */
    private String label;

    /** Trigger look. When null: {@link Variant#BUTTON} if a {@link #label} is set, else {@link Variant#ICON}. */
    private Variant variant;

    /** Which edge the popover aligns to. Defaults to {@link Align#END} when null. */
    private Align align;

    public static UiMenuButton of(UiMenuItem... items) {
        var b = new UiMenuButton();
        b.items = new ArrayList<>(List.of(items));
        return b;
    }

    public static UiMenuButton of(String id, UiMenuItem... items) {
        var b = of(items);
        b.setId(id);
        return b;
    }

    public UiMenuButton icon(String iconToken) {
        this.icon = iconToken;
        return this;
    }

    public UiMenuButton label(String label) {
        this.label = label;
        return this;
    }

    public UiMenuButton variant(Variant variant) {
        this.variant = variant;
        return this;
    }

    public UiMenuButton align(Align align) {
        this.align = align;
        return this;
    }

    public UiMenuButton item(UiMenuItem item) {
        if (this.items == null) this.items = new ArrayList<>();
        this.items.add(item);
        return this;
    }
}
