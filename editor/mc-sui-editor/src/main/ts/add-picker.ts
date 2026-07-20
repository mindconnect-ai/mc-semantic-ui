/*
 * Tiny modal picker that lets the user choose which type to add. Used both
 * for "set a root when the tree is empty" (full schema) and for
 * "add a child to <container>" (filtered by the parent's allowedTypes).
 *
 * Built on the native <dialog> element so we don't carry our own focus
 * trap / outside-click logic. Returns a promise that resolves with the
 * picked NodeType or null on cancel.
 */
import type { NodeMeta, NodeType } from "./types.js";

export function pickType(options: NodeMeta[], title: string): Promise<NodeType | null> {
    return new Promise((resolve) => {
        const dialog = document.createElement("dialog");
        dialog.className = "sui-editor-picker";
        dialog.innerHTML =
            `<form method="dialog" class="sui-editor-picker-body">` +
            `  <h3>${escapeHtml(title)}</h3>` +
            `  <ul class="sui-editor-picker-list">` +
            options.map(o =>
                `<li><button type="button" data-type="${escapeAttr(o.type)}">` +
                `  <span class="sui-tree-type">${escapeHtml(o.type)}</span>` +
                `  <span>${escapeHtml(o.label)}</span>` +
                `  <small>${escapeHtml(o.category)}</small>` +
                `</button></li>`
            ).join("") +
            `  </ul>` +
            `  <menu><button type="button" data-action="cancel">Cancel</button></menu>` +
            `</form>`;
        document.body.appendChild(dialog);

        const cleanup = (result: NodeType | null) => {
            dialog.close();
            dialog.remove();
            resolve(result);
        };

        dialog.addEventListener("click", (e) => {
            const target = e.target as HTMLElement | null;
            if (!target) return;
            // Backdrop click closes (the form's bounding rect doesn't cover the backdrop).
            const rect = dialog.getBoundingClientRect();
            const insideDialog =
                e.clientX >= rect.left && e.clientX <= rect.right &&
                e.clientY >= rect.top  && e.clientY <= rect.bottom;
            if (!insideDialog) { cleanup(null); return; }

            const cancelBtn = target.closest<HTMLButtonElement>("[data-action='cancel']");
            if (cancelBtn) { cleanup(null); return; }

            const typeBtn = target.closest<HTMLButtonElement>("button[data-type]");
            if (typeBtn) {
                cleanup(typeBtn.dataset.type ?? null);
            }
        });

        dialog.addEventListener("cancel", () => cleanup(null));
        dialog.showModal();
    });
}

function escapeHtml(s: string): string {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
function escapeAttr(s: string): string {
    return s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
