import type { UiTree, UiTreeNode } from "../model.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { cls, evt } from "./util.js";

/**
 * Renders a {@link UiTree}: a list of recursive {@link UiTreeNode}s.
 *
 * <p>Every tree node is a full {@code UiNode} (type {@code "tree-node"})
 * rendered through the renderer registry — so a {@code REPLACE} patch
 * targeting a node's id re-renders exactly that row (and its subtree), and
 * a {@code REMOVE} patch drops it.
 *
 * <p>Each node with children (or {@code content}) renders as a native
 * {@code <details>} disclosure; leaf nodes render as a plain row. Expand /
 * collapse is client-controlled — every disclosure carries
 * {@code data-sui-client-collapse} so the morpher preserves the user's manual
 * open/close across server re-renders; the server only sets the initial state
 * via {@code node.open}.
 *
 * <p>Clicking a node's label fires its {@code onClick} trigger. Because the
 * event bus calls {@code preventDefault()} on {@code [data-trigger]} clicks,
 * the label click does <em>not</em> toggle the row — only the twisty / the rest
 * of the summary does. Leaf nodes and non-clickable labels behave as expected.
 */
export function renderTree(node: UiTree, r: SuiRenderer): string {
    const roots = (node.nodes || []).map(n => renderChild(n, r)).join("");
    return `<div class="${cls("sui-tree", node)}"${evt(node)} id="${escapeHtml(node.id)}">
        ${node.title ? `<h2 class="sui-tree-title">${escapeHtml(node.title)}</h2>` : ""}
        <ul class="sui-tree-list" role="tree">${roots}</ul>
    </div>`;
}

/**
 * Dispatches a child through the renderer registry so registered overrides
 * apply. Lenient fallback: legacy data without a {@code type} discriminator
 * (pre "tree-node" JSON) still renders directly.
 */
function renderChild(node: UiTreeNode, r: SuiRenderer): string {
    return node.type ? r.render(node) : renderTreeNode(node, r);
}

function renderLabel(node: UiTreeNode, r: SuiRenderer): string {
    const icon = node.icon ? `<span class="sui-tree-icon">${renderIcon(node.icon)}</span>` : "";
    const inner = node.labelNode ? r.render(node.labelNode) : escapeHtml(node.label ?? "");
    const labelCls = `sui-tree-label${node.selected ? " is-selected" : ""}`;
    // A clickable label is an anchor carrying the trigger; the event bus
    // intercepts the click (preventDefault) so it fires without toggling the
    // enclosing <details>. A static label is a plain span.
    const label = node.onClick
        ? `<a class="${labelCls}" href="#" data-trigger='${encodeTrigger(node.onClick)}'>${inner}</a>`
        : `<span class="${labelCls}">${inner}</span>`;
    return `${icon}${label}`;
}

/** Renders one tree row (a {@code <li>}). Registered under {@code "tree-node"}. */
export function renderTreeNode(node: UiTreeNode, r: SuiRenderer): string {
    const children = node.children || [];
    const expandable = children.length > 0 || node.content != null;
    const labelHtml = renderLabel(node, r);

    if (!expandable) {
        return `<li class="sui-tree-node sui-tree-node--leaf" id="${escapeHtml(node.id)}" data-id="${escapeHtml(node.id)}" role="treeitem">
            <div class="sui-tree-row">${labelHtml}</div>
        </li>`;
    }

    const content = node.content
        ? `<div class="sui-tree-content">${r.render(node.content)}</div>`
        : "";
    const childList = children.length > 0
        ? `<ul class="sui-tree-children" role="group">${children.map(c => renderChild(c, r)).join("")}</ul>`
        : "";

    return `<li class="sui-tree-node" id="${escapeHtml(node.id)}" data-id="${escapeHtml(node.id)}" role="treeitem" aria-expanded="${node.open ? "true" : "false"}">
        <details class="sui-tree-item" data-sui-client-collapse${node.open ? " open" : ""}>
            <summary class="sui-tree-row sui-tree-summary">${labelHtml}</summary>
            <div class="sui-tree-body">${content}${childList}</div>
        </details>
    </li>`;
}
