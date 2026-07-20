import type { UiText } from "../model.js";
import { escapeHtml } from "../renderer.js";
import { cls, evt } from "./util.js";

export function renderText(node: UiText): string {
    const id = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    return `<span class="${cls("sui-text", node)}"${evt(node)}${id}>${escapeHtml(node.text ?? "")}</span>`;
}
