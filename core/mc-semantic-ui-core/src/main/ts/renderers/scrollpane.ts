import type { UiScrollPane } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * A scrolling viewport around one child. Parity with {@code scrollpane.hbs}.
 * The {@code data-stick-latest} marker is picked up by the bus's auto-enhance
 * (wireAutoScroll) for the live-feed behaviour; without JS the pane just
 * scrolls.
 */
export function renderScrollPane(node: UiScrollPane, r: SuiRenderer): string {
    const style = node.maxHeight ? ` style="max-height:${escapeHtml(node.maxHeight)}"` : "";
    const stick = node.stickToLatest ? " data-stick-latest" : "";
    const content = node.content ? r.render(node.content) : "";
    return `<div class="${cls("sui-scrollpane", node)}"${evt(node)} id="${escapeHtml(node.id)}"${style}${stick}>${content}</div>`;
}
