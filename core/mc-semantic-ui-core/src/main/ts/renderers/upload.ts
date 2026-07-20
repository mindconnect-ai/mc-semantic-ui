import type { UiUpload } from "../model.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Renders a {@link UiUpload} drop zone: a hidden {@code <input type="file">}
 * plus a styled area the user can drop files onto, with a browse button (a
 * {@code <label for>} that opens the native picker without JS).
 *
 * <p>The upload trigger is emitted as {@code data-upload-trigger} — a
 * deliberately separate attribute from the click-owned {@code data-trigger} so
 * clicking the zone doesn't fire it; the {@code SuiEventBus} reads it on
 * {@code drop} and on the input's {@code change} (see the upload handling in
 * {@code eventbus.ts}). The multipart field name rides along in
 * {@code data-sui-upload-name}.
 */
export function renderUpload(node: UiUpload, _r: SuiRenderer): string {
    const id = escapeHtml(node.id);
    const inputId = `${id}__input`;
    const name = escapeHtml(node.name || node.id);
    const accept = node.accept ? ` accept="${escapeHtml(node.accept)}"` : "";
    const multiple = node.multiple ? " multiple" : "";
    const trigger = node.onUpload ? ` data-upload-trigger='${encodeTrigger(node.onUpload)}'` : "";
    const dropText = escapeHtml(node.dropText ?? "Drag files here or");
    const buttonLabel = escapeHtml(node.buttonLabel ?? "Browse…");

    return `<div class="${cls("sui-upload", node)}"${evt(node)} id="${id}" data-sui-upload data-sui-upload-name="${name}"${trigger}>
        <input type="file" id="${inputId}" name="${name}" class="sui-upload-input"${accept}${multiple} hidden>
        ${node.label ? `<div class="sui-upload-label">${escapeHtml(node.label)}</div>` : ""}
        <div class="sui-upload-zone">
            <span class="sui-upload-icon" aria-hidden="true">⬆</span>
            <span class="sui-upload-prompt">${dropText}</span>
            <label for="${inputId}" class="sui-btn sui-btn--secondary sui-upload-browse">${buttonLabel}</label>
        </div>
        ${node.hint ? `<small class="sui-hint sui-upload-hint">${escapeHtml(node.hint)}</small>` : ""}
    </div>`;
}
