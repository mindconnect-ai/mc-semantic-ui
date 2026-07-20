import type { UiDetail } from "../model.js";
import { escapeHtml } from "../renderer.js";
import { cls, evt } from "./util.js";
import { renderActions, renderLinks } from "./shared.js";

export function renderDetail(node: UiDetail): string {
    const fields = (node.fields || []).map(f =>
        `<div class="sui-detail-row">
            <dt>${escapeHtml(f.label)}</dt>
            <dd>${f.value != null ? escapeHtml(f.value) : '<span class="sui-empty">—</span>'}</dd>
        </div>`
    ).join("");
    return `<div class="${cls("sui-detail", node)}"${evt(node)} id="${escapeHtml(node.id)}">
        ${node.title ? `<h2>${escapeHtml(node.title)}</h2>` : ""}
        <dl class="sui-detail-grid">${fields}</dl>
        <div class="sui-form-footer">
            ${renderActions(node.actions || [])}
            ${renderLinks(node.links || [])}
        </div>
    </div>`;
}
