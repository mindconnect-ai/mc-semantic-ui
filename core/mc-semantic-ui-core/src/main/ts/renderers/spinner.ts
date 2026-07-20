import type { UiSpinner } from "../model.js";
import { escapeHtml } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Renders a {@link UiSpinner}: a spinning glyph, optionally with a label.
 *
 * <p>The glyph is a CSS-animated ring ({@code .sui-spinner-glyph}); size is a
 * modifier class. {@code role="status"} + an accessible label expose it to
 * assistive tech (the {@code title} or the visible {@code label}, whichever is
 * present). A purely decorative spinner with no title/label stays silent.
 */
export function renderSpinner(node: UiSpinner): string {
    const size = (node.size || "MD").toLowerCase();
    const a11yLabel = node.title || node.label;
    const a11y = a11yLabel
        ? `role="status" aria-label="${escapeHtml(a11yLabel)}"`
        : `role="status" aria-hidden="true"`;
    const id = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    const label = node.label
        ? `<span class="sui-spinner-label">${escapeHtml(node.label)}</span>`
        : "";
    return `<span${id} class="${cls(`sui-spinner sui-spinner--${size}`, node)}"${evt(node)} ${a11y}>` +
        `<span class="sui-spinner-glyph"></span>${label}</span>`;
}
