import type { UiTableRow } from "../model.js";
import { escapeHtml } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Standalone render of a single {@link UiTableRow}. Inside a real table
 * the row is a {@code <tr>}; outside one it's a key/value list of the
 * row's data fields. Useful for the editor preview when the user selects
 * a row in the tree.
 */
export function renderRow(node: UiTableRow): string {
    const idAttr = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    const data = node.data ?? {};
    const entries = Object.keys(data);
    const items = entries.length === 0
        ? `<dd class="sui-row-empty">(no data)</dd>`
        : entries
            .map(key => `<dt>${escapeHtml(key)}</dt><dd>${escapeHtml(data[key])}</dd>`)
            .join("");
    return `<dl class="${cls("sui-row", node)}"${evt(node)}${idAttr}>${items}</dl>`;
}
