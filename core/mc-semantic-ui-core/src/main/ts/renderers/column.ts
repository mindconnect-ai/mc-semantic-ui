import type { UiTableColumn } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Standalone render of a single {@link UiTableColumn}. Inside a real
 * table the column is just a {@code <th>}; outside one it's a stub — the
 * editor preview uses this to show the user what they selected when they
 * click a column in the tree. Shows label + dataKey + the cellTemplate
 * (if any) so the author sees their per-cell template even without rows.
 */
export function renderColumn(node: UiTableColumn, r: SuiRenderer): string {
    const idAttr = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    const label = escapeHtml(node.label ?? "");
    const dataKey = node.dataKey ? `<small class="sui-column-key">${escapeHtml(node.dataKey)}</small>` : "";
    const template = node.cellTemplate
        ? `<div class="sui-column-template"><small>cellTemplate:</small>${r.render(node.cellTemplate)}</div>`
        : "";
    return `<div class="${cls("sui-column", node)}"${evt(node)}${idAttr}>` +
           `<strong class="sui-column-label">${label}</strong>` +
           dataKey + template +
           `</div>`;
}
