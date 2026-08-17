import type { UiList } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";
import { renderActions, renderPagination } from "./shared.js";
import { renderIcon } from "./icon.js";

export function renderList(node: UiList, r: SuiRenderer): string {
    const items = (node.items || []).map(item => r.renderItem(item)).join("");
    return `<div class="${cls("sui-list", node)}"${evt(node)} id="${escapeHtml(node.id)}">
        <div class="sui-list-header">
            ${node.title ? `<h2>${node.icon ? renderIcon(node.icon) : ""}${escapeHtml(node.title)}</h2>` : ""}
            ${node.headerExtra ? `<div class="sui-header-extra">${r.render(node.headerExtra)}</div>` : ""}
            <div class="sui-actions">${renderActions(node.actions || [])}</div>
        </div>
        <ul>${items}</ul>
        ${node.pagination ? renderPagination(node.pagination) : ""}
    </div>`;
}
