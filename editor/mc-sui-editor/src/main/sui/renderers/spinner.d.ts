import type { UiSpinner } from "../model.js";
/**
 * Renders a {@link UiSpinner}: a spinning glyph, optionally with a label.
 *
 * <p>The glyph is a CSS-animated ring ({@code .sui-spinner-glyph}); size is a
 * modifier class. {@code role="status"} + an accessible label expose it to
 * assistive tech (the {@code title} or the visible {@code label}, whichever is
 * present). A purely decorative spinner with no title/label stays silent.
 */
export declare function renderSpinner(node: UiSpinner): string;
