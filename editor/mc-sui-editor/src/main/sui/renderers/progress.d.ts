import type { UiProgress } from "../model.js";
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
export declare function renderProgress(node: UiProgress): string;
