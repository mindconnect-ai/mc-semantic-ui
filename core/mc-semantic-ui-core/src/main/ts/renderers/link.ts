import type { UiLink } from "../model.js";
import { escapeHtml, encodeTrigger } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { cls, evt } from "./util.js";

/**
 * Renders a single {@link UiLink} as a standalone {@code <a>}. The wrapper
 * id="…" follows the same convention as every other node type, so the
 * editor's id-based selection finds it. {@code href} drives the navigation;
 * {@code data-href} is a hint the EventBus reads to route via the SPA when
 * present.
 *
 * <p>Cast through {@code any} for {@code id} because the TS {@link UiLink}
 * interface omits the UiNode-inherited id field (Java-side it's there).
 * The runtime payload always carries it from the server.
 */
export function renderLink(node: UiLink): string {
    const id = (node as any).id as string | undefined;
    const idAttr = id ? ` id="${escapeHtml(id)}"` : "";
    const href = escapeHtml(node.href ?? "#");
    const icon = node.icon ? `${renderIcon(node.icon)} ` : "";
    const label = `${icon}${escapeHtml(node.label ?? "")}`;
    // External links open in a new browser tab. We deliberately OMIT
    // data-href so the EventBus click handler doesn't intercept and SPA-route
    // them — target="_blank" only works when the native click is allowed
    // through. (onClick is ignored for external links.)
    if (node.external) {
        return `<a${idAttr} class="${cls("sui-link", node as any)}"${evt(node, "click")} href="${href}" target="_blank" rel="noopener noreferrer">${label}</a>`;
    }
    // A link with an onClick trigger dispatches through the bus (data-trigger) —
    // it fires a fetch/patch and gets inline loading, with href as the no-JS
    // fallback. Without onClick it's a plain navigation (data-href hint).
    if (node.onClick) {
        return `<a${idAttr} class="${cls("sui-link", node as any)}" href="${href}" data-trigger='${encodeTrigger(node.onClick)}'>${label}</a>`;
    }
    return `<a${idAttr} class="${cls("sui-link", node as any)}" href="${href}" data-href="${href}">${label}</a>`;
}
