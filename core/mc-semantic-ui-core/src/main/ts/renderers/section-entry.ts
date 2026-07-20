import type { UiSectionEntry } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Standalone render of a single {@link UiSectionEntry}. Normally entries
 * are rendered inline by the section's tab+panel template, but the editor
 * preview can address a single entry directly when the user selects it in
 * the tree. We emit a labelled box with the entry's title above its content
 * — enough context to read the structure without surrounding tabs.
 */
export function renderSectionEntry(node: UiSectionEntry, r: SuiRenderer): string {
    const idAttr = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    const title = node.title
        ? `<h3 class="sui-section-entry-title">${escapeHtml(node.title)}</h3>`
        : "";
    const body = node.content ? r.render(node.content) : "";
    return `<div class="${cls("sui-section-entry", node)}"${evt(node)}${idAttr}>${title}${body}</div>`;
}
