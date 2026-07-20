package ai.mindconnect.ui.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of a {@link UiTree}. Recursive: a node with {@link #children}
 * (or {@link #content}) is <em>expandable</em> and renders as a native
 * {@code <details>} disclosure; a node with neither is a leaf and renders
 * as a plain row.
 *
 * <p>A full {@link UiNode} subtype (type discriminator {@code "tree-node"}),
 * not a nested value class — so every tree row is individually addressable
 * by {@link UiPatch}: {@code REPLACE} its id to re-render one row (e.g. a
 * changed status badge), {@code REMOVE} its id to drop it from the tree.
 *
 * <p>Nodes may carry an {@link #icon}, a plain-text {@link #label} (or a
 * rich {@link #labelNode}), an {@link #onClick} trigger, and arbitrary
 * nested {@link #content}. Expand/collapse is client-controlled — the
 * server only decides the <em>initial</em> state via {@link #open}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UiTreeNode extends UiNode {

    /** Plain-text label. Kept as the accessible fallback when {@link #labelNode} is set. */
    private String label;
    /**
     * Optional rich label: rendered as the row title instead of the plain
     * {@link #label} text — e.g. a name plus a status badge via a
     * {@code UiStack}. The {@link #label} string remains the fallback.
     */
    private UiNode labelNode;
    /** Optional leading icon/emoji shown before the label (e.g. "📁", "•"). */
    private String icon;
    /**
     * Optional rich body rendered inside the node (above its children) when
     * expanded. Lets a tree row carry an arbitrary component — a detail
     * panel, a form, a chart — not just nested nodes.
     */
    private UiNode content;
    /** Child nodes. A node with children (or {@link #content}) is expandable. */
    private List<UiTreeNode> children = new ArrayList<>();
    /** Initial expanded state of the disclosure. User toggles override it thereafter. */
    private boolean open;
    /** When true, the row is rendered with a selected/highlighted style. */
    private boolean selected;

    public static UiTreeNode of(String id, String label) {
        var n = new UiTreeNode();
        n.setId(id);
        n.label = label;
        return n;
    }

    public UiTreeNode icon(String icon)              { this.icon = icon;           return this; }
    public UiTreeNode labelNode(UiNode node)         { this.labelNode = node;      return this; }
    public UiTreeNode onClick(UiTrigger trigger)     { setOnClick(trigger);         return this; }
    /** Convenience: plain navigation link (GET, render page). */
    public UiTreeNode href(String href)              { setOnClick(UiTrigger.go(href)); return this; }
    public UiTreeNode content(UiNode content)        { this.content = content;     return this; }
    public UiTreeNode child(UiTreeNode child)        { children.add(child);        return this; }
    public UiTreeNode open(boolean open)             { this.open = open;           return this; }
    public UiTreeNode selected(boolean selected)     { this.selected = selected;   return this; }
}
