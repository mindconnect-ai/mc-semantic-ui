import type { UiList } from "../model.js";
import type { OutlineHandler } from "../snapshot.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";
import { renderActions, renderPagination } from "./shared.js";
import { renderIcon } from "./icon.js";

export function renderList(node: UiList, r: SuiRenderer): string {
    const items = (node.items || []).map(item => r.render(item)).join("");
    return `<div class="${cls("sui-list", node)}"${evt(node)} id="${escapeHtml(node.id)}">
        <div class="sui-list-header">
            ${node.title ? `<h2>${node.icon ? renderIcon(node.icon) : ""}${escapeHtml(node.title)}</h2>` : ""}
            ${node.headerExtra ? `<div class="sui-header-extra">${r.render(node.headerExtra)}</div>` : ""}
            <div class="sui-actions">${renderActions(node.actions || [], r)}</div>
        </div>
        <ul>${items}</ul>
        ${node.pagination ? renderPagination(node.pagination) : ""}
    </div>`;
}

/**
 * What a list says about itself: its title, its rows — each with the label,
 * the description and whether the row itself is clickable — and the buttons
 * in its header. A row's own content and actions are handed on as children,
 * so each of them is described by whatever type it is.
 */
export const outlineList: OutlineHandler<UiList> = (node) => ({
    title: node.title,
    items: (node.items ?? []).map(item => ({
        id: item.id,
        label: item.label,
        description: item.description,
        clickable: !!item.onClick,
        children: [
            ...(item.actions ?? []),
            ...(item.collapseSummaryNode ? [item.collapseSummaryNode] : []),
            ...(item.content ? [item.content] : []),
            ...(item.labelNode ? [item.labelNode] : []),
        ],
    })),
    pagination: node.pagination
        ? { page: node.pagination.page, size: node.pagination.size, total: node.pagination.total }
        : undefined,
    children: [...(node.actions ?? []), ...(node.headerExtra ? [node.headerExtra] : [])],
});
