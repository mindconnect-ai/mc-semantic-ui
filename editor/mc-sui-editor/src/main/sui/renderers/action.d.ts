import type { UiAction } from "../model.js";
/**
 * Renders one UiAction. The {@code appearance} field picks the DOM shape;
 * all variants share the same {@code data-action} / {@code data-trigger} /
 * {@code data-confirm} attributes so the central click dispatcher handles
 * them uniformly.
 */
export declare function renderAction(a: UiAction): string;
