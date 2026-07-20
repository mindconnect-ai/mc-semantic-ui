import type { UiDialog } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";

/**
 * Renders a {@link UiDialog} as a fixed-position modal overlay (backdrop +
 * centred box). Because the box is fixed-positioned it overlays the viewport
 * wherever the node sits in the DOM — so a dialog is "opened" by APPENDing it
 * into the dialog host and "closed" by REMOVE-ing it by id. Several dialogs can
 * coexist; the outer host carries the dialog's own id so a REMOVE targets it.
 *
 * <p>Mirrors the SSR {@code dialog.hbs} template. The {@code data-sui-dialog-close}
 * marker on the × and the backdrop is what the event bus intercepts to REMOVE
 * this dialog (or, in SSR, the anchor's {@code closeHref} navigates).
 */
export function renderDialog(node: UiDialog, r: SuiRenderer): string {
    const id = escapeHtml(node.id ?? "");
    const title = node.title
        ? `<h2 class="sui-dialog-title">${escapeHtml(node.title)}</h2>`
        : `<span></span>`;
    const close = node.closeHref
        ? `<a class="sui-dialog-close" data-sui-dialog-close href="${escapeHtml(node.closeHref)}" aria-label="Close">×</a>`
        : `<button type="button" class="sui-dialog-close" data-sui-dialog-close aria-label="Close">×</button>`;
    const body = node.node ? r.render(node.node) : "";
    return (
        `<div class="sui-dialog-host" id="${id}" data-close-href="${escapeHtml(node.closeHref ?? "")}">` +
        `<div class="sui-dialog-backdrop" data-sui-dialog-close></div>` +
        `<div class="sui-dialog" role="dialog" aria-modal="true"${node.title ? ` aria-label="${escapeHtml(node.title)}"` : ""}>` +
        `<div class="sui-dialog-header">${title}${close}</div>` +
        `<div class="sui-dialog-body">${body}</div>` +
        `</div></div>`
    );
}
