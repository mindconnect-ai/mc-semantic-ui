import type { UiAppShell, UiHeader, UiMenu } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Application frame: header on top, menu beside the content, optional footer
 * across the bottom. Parity with {@code app-shell.hbs}.
 *
 * <p>The node's whole point is that the error-prone parts are handled here
 * instead of by the caller:
 * <ul>
 *   <li>the header's burger is pointed at the menu's id, and the menu's own
 *       toggle is switched off — one burger, not two;</li>
 *   <li>the shell fills the viewport by default, so the sidebar reaches the
 *       bottom of the window and the content scrolls inside it;</li>
 *   <li>the content area is a <em>slot</em> ({@code data-sui-slot}), so a
 *       REPLACE patch aimed at it fills it instead of deleting the container.</li>
 * </ul>
 * Header and menu are copied before those adjustments — the caller's nodes are
 * never mutated, which matters because the same menu object is usually reused
 * across pages.
 */
export function renderAppShell(node: UiAppShell, r: SuiRenderer): string {
    const shellId = node.id || "shell";
    const menuId = node.menu?.id;

    // Copy, don't mutate: the caller may hold this menu for the next page.
    const menu: UiMenu | undefined = node.menu
        ? { ...node.menu, toggle: false }
        : undefined;
    const header: UiHeader | undefined = node.header
        ? { ...node.header, menuToggle: menuId ?? node.header.menuToggle }
        : undefined;

    const headerHtml = header ? r.render(header) : "";
    const menuHtml = menu ? r.render(menu) : "";
    const contentHtml = node.content ? r.render(node.content) : "";
    const footerHtml = node.footer
        ? `<div class="sui-shell-footer">${r.render(node.footer)}</div>`
        : "";

    const content = `<div class="sui-shell-content" id="${escapeHtml(shellId)}-content" data-sui-slot="content">${contentHtml}</div>`;

    // Menu first for a LEFT sidebar, after the content for a RIGHT one — the
    // drawer variants position themselves, but PUSH relies on DOM order.
    const body = node.menu?.side === "RIGHT"
        ? `${content}${menuHtml}`
        : `${menuHtml}${content}`;

    // Filling the viewport is the default, as in every other admin layout.
    // fillViewport: false hands height control back to the container — for a
    // shell embedded in a docs page, a preview pane or a dashboard tile.
    const base = node.fillViewport === false ? "sui-shell sui-shell--fit" : "sui-shell";

    return `<div class="${cls(base, node)}"${evt(node)} id="${escapeHtml(shellId)}" data-sui="app-shell">`
        + headerHtml
        + `<div class="sui-shell-body">${body}</div>`
        + footerHtml
        + `</div>`;
}
