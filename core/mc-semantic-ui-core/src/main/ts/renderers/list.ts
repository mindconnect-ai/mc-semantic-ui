import type { UiList } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";
import { renderActions, renderPagination } from "./shared.js";

export function renderList(node: UiList, r: SuiRenderer): string {
    const items = (node.items || []).map(item => r.renderItem(item)).join("");
    return `<div class="${cls("sui-list", node)}"${evt(node)} id="${escapeHtml(node.id)}">
        <div class="sui-list-header">
            ${node.title ? `<h2>${escapeHtml(node.title)}</h2>` : ""}
            <div class="sui-actions">${renderActions(node.actions || [])}</div>
        </div>
        <ul>${items}</ul>
        ${node.pagination ? renderPagination(node.pagination) : ""}
    </div>`;
}
