package ai.mindconnect.ui.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic, recursive tree widget: a list of root {@link UiTreeNode}s, each
 * of which may hold its own child {@link UiTreeNode}s to any depth.
 *
 * <p>Every tree node is a full {@link UiNode} (type {@code "tree-node"}), so
 * individual rows are patch-addressable: {@code REPLACE} a node's id to
 * re-render just that row, {@code REMOVE} it to drop it from the tree.
 *
 * <p>A node that has children (or {@link UiTreeNode#getContent() content}) is
 * <em>expandable</em> and renders as a native {@code <details>} disclosure; a
 * node with neither is a leaf and renders as a plain row. Expand/collapse is
 * <b>client-controlled</b>: every disclosure is tagged
 * {@code data-sui-client-collapse} so the user's manual open/close survives
 * server re-renders and streaming patches — the server only decides the
 * <em>initial</em> state via {@link UiTreeNode#isOpen() open}.
 *
 * <p><b>Renderer support:</b> the tree is rendered by the TypeScript SPA
 * renderer only for now; there is no Handlebars SSR template, so an SSR pass
 * falls back to a {@code <pre>} JSON dump (see {@code SuiServerRenderer}).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UiTree extends UiNode {

    private List<UiTreeNode> nodes = new ArrayList<>();

    public UiTree node(UiTreeNode node) { nodes.add(node); return this; }

    public static UiTree of(String id, String title) {
        var t = new UiTree();
        t.setId(id); t.setTitle(title);
        return t;
    }
}
