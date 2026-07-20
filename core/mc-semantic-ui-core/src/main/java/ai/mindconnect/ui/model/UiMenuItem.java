package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry in a {@link UiMenu} — a link (or a group that nests further
 * entries). Like {@code UiTreeNode}, every item is a full {@link UiNode}
 * (type {@code "menu-item"}) so a patch can {@code REPLACE} a single entry (to
 * flip its {@code selected} state, swap its label, add a badge) without
 * re-rendering the whole menu.
 *
 * <p>A menu item <b>is a</b> {@link UiAction}: it inherits the full clickable
 * behaviour — {@link #onClick} plus {@link #confirm}, {@link #icon},
 * {@code label}, {@code enabled}/{@code disabledReason} and {@code loading} — so
 * anything you can express on a button (a confirm dialog before a destructive
 * action, a disabled state with a reason) works identically here. On top of the
 * action it adds the menu-specific fields below.
 *
 * <p>A leaf item renders as an {@code <a>}: {@link #href} drives navigation
 * (works with no JS); an {@link #onClick} makes it dispatch through the event
 * bus instead. An item that has {@link #children} renders as a collapsible group
 * (native {@code <details>}), and when the menu is collapsed to its icon rail
 * the children appear as a hover fly-out.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiMenuItem extends UiAction {

    /** Navigation target for a leaf item; the no-JS fallback when {@link #onClick} is set. */
    private String href;
    /** Marks the current item — highlighted as active. */
    private boolean selected;
    /**
     * Optional trailing badge — a short count or status ({@code "3"},
     * {@code "new"}) shown after the label; in the rail it shrinks to a dot on
     * the icon.
     */
    private String badge;
    /** When true (and this item has children), the group renders initially open. */
    private boolean open;
    /**
     * Marks a destructive action (Delete, Remove …) — rendered in the danger
     * colour. Most useful inside a {@link UiMenuButton} popover.
     */
    private boolean danger;
    /**
     * When true this entry is a non-interactive separator line, not a link. Its
     * other fields are ignored. A thin rule between groups of items, chiefly in
     * a {@link UiMenuButton} popover.
     */
    private boolean divider;
    /** Nested entries; when non-empty this item is a collapsible/fly-out group. */
    private List<UiMenuItem> children;

    public static UiMenuItem of(String id, String label) {
        var i = new UiMenuItem();
        i.setId(id);
        i.setLabel(label);
        return i;
    }

    /** A leaf link: id + label + navigation href. */
    public static UiMenuItem link(String id, String label, String href) {
        var i = of(id, label);
        i.href = href;
        return i;
    }

    /** A group holding nested items. */
    public static UiMenuItem group(String id, String label, UiMenuItem... children) {
        var i = of(id, label);
        i.children = new ArrayList<>(List.of(children));
        return i;
    }

    // ── fluent builders (covariant overrides so chaining stays UiMenuItem) ──

    @Override
    public UiMenuItem icon(String iconToken) {
        setIcon(iconToken);
        return this;
    }

    @Override
    public UiMenuItem onClick(UiTrigger trigger) {
        setOnClick(trigger);
        return this;
    }

    /** Confirm-dialog text shown before {@link #onClick} fires (same as {@link UiAction#confirm}). */
    @Override
    public UiMenuItem confirm(String message) {
        super.confirm(message);
        return this;
    }

    public UiMenuItem href(String href) {
        this.href = href;
        return this;
    }

    public UiMenuItem selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public UiMenuItem badge(String badge) {
        this.badge = badge;
        return this;
    }

    public UiMenuItem open(boolean open) {
        this.open = open;
        return this;
    }

    public UiMenuItem danger(boolean danger) {
        this.danger = danger;
        return this;
    }

    /** A non-interactive separator entry for a {@link UiMenuButton} popover. */
    public static UiMenuItem divider() {
        var i = new UiMenuItem();
        i.divider = true;
        return i;
    }

    public UiMenuItem child(UiMenuItem item) {
        if (this.children == null) this.children = new ArrayList<>();
        this.children.add(item);
        return this;
    }
}
