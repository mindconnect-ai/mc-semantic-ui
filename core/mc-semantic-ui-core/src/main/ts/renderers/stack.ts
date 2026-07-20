import type { UiStack } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Plain composition box: renders each child in order, no chrome. Direction
 * and gap are surfaced as CSS so the host stylesheet can override with
 * tokens. Parity with {@code stack.hbs}.
 */
export function renderStack(node: UiStack, r: SuiRenderer): string {
    const dir = (node.direction ?? "VERTICAL").toLowerCase();
    const gapStyle = node.gap != null ? ` style="gap: ${node.gap}px"` : "";
    const id = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    const children = (node.children ?? []).map(c => r.render(c)).join("");
    return `<div class="${cls(`sui-stack sui-stack--${dir}`, node)}"${evt(node)}${id}${gapStyle}>${children}</div>`;
}
