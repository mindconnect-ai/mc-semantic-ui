import type { UiFieldGroup } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Renders a {@link UiFieldGroup} as a native {@code <fieldset>} with a
 * {@code <legend>} title, so the group heading is associated with every field
 * inside. Children go through the dispatcher, so a group can hold fields or any
 * layout node. Parity with {@code fieldgroup.hbs}.
 */
export function renderFieldGroup(node: UiFieldGroup, r: SuiRenderer): string {
    const id = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    const legend = node.title ? `<legend class="sui-fieldgroup-title">${escapeHtml(node.title)}</legend>` : "";
    const hint = node.hint ? `<small class="sui-hint">${escapeHtml(node.hint)}</small>` : "";
    const children = (node.content ?? []).map(c => r.render(c)).join("");
    return `<fieldset class="${cls("sui-fieldgroup", node)}"${evt(node)}${id}>${legend}${hint}${children}</fieldset>`;
}
