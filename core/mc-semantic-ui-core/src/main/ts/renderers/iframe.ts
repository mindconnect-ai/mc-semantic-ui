import type { UiIFrame } from "../model.js";
import { escapeHtml } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * An embedded browsing context. Parity with {@code iframe.hbs}: a height
 * caps the frame in normal flow, without one it fills a flex-column parent
 * (the .sui-iframe default in sui.css).
 */
export function renderIFrame(node: UiIFrame): string {
    const title = node.title ? ` title="${escapeHtml(node.title)}"` : "";
    const style = node.height ? ` style="height:${escapeHtml(node.height)}; flex:none"` : "";
    const sandbox = node.sandbox ? ` sandbox="${escapeHtml(node.sandbox)}"` : "";
    return `<iframe class="${cls("sui-iframe", node)}"${evt(node)} id="${escapeHtml(node.id)}" src="${escapeHtml(node.src ?? "")}"${title}${style}${sandbox} loading="lazy"></iframe>`;
}
