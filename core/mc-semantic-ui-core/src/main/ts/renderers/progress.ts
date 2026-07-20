import type { UiProgress } from "../model.js";
import { escapeHtml } from "../renderer.js";
import { cls, evt } from "./util.js";

/**
 * Renders a {@link UiProgress}: a horizontal bar or a circular ring.
 *
 * <p>When {@code value} is a number it renders determinate progress (the fill
 * width / ring dash-offset reflects {@code value / max}); when {@code value} is
 * absent it renders an indeterminate loop driven purely by CSS. {@code status}
 * adds a colour-intent modifier class. Accessibility follows the ARIA
 * progressbar pattern ({@code aria-valuenow/min/max}, or {@code aria-busy} while
 * indeterminate).
 */
export function renderProgress(node: UiProgress): string {
    const max = node.max && node.max > 0 ? node.max : 100;
    const indeterminate = typeof node.value !== "number";
    const pct = indeterminate
        ? 0
        : Math.max(0, Math.min(100, Math.round((node.value! / max) * 100)));
    const variant = (node.variant || "BAR") === "CIRCLE" ? "circle" : "bar";
    const status = (node.status || "NORMAL").toLowerCase();
    const showValue = node.showValue !== false && !indeterminate;

    const rootCls = cls(
        `sui-progress sui-progress--${variant} sui-progress--${status}` +
        (indeterminate ? " sui-progress--indeterminate" : ""),
        node);
    const id = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    const aria = indeterminate
        ? `role="progressbar" aria-busy="true"`
        : `role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"`;
    const titleAttr = node.title ? ` aria-label="${escapeHtml(node.title)}"` : "";

    if (variant === "circle") {
        // r chosen so the circumference is exactly 100 → dashoffset = 100 − pct.
        const dashoffset = indeterminate ? 75 : 100 - pct;
        const text = showValue
            ? `<text class="sui-progress-ring-text" x="18" y="20.5" text-anchor="middle">${pct}%</text>`
            : "";
        return `<div${id}${evt(node)} class="${rootCls}" ${aria}${titleAttr}>` +
            `<svg class="sui-progress-ring" viewBox="0 0 36 36" width="40" height="40">` +
            `<circle class="sui-progress-ring-track" cx="18" cy="18" r="15.9155" fill="none" stroke-width="3"></circle>` +
            `<circle class="sui-progress-ring-fill" cx="18" cy="18" r="15.9155" fill="none" stroke-width="3" ` +
            `transform="rotate(-90 18 18)" stroke-dasharray="100" stroke-dashoffset="${dashoffset}"></circle>` +
            `${text}</svg></div>`;
    }

    const fillStyle = indeterminate ? "" : ` style="width:${pct}%"`;
    const text = showValue
        ? `<span class="sui-progress-text">${pct}%</span>`
        : "";
    return `<div${id}${evt(node)} class="${rootCls}" ${aria}${titleAttr}>` +
        `<div class="sui-progress-track"><div class="sui-progress-fill"${fillStyle}></div></div>` +
        `${text}</div>`;
}
