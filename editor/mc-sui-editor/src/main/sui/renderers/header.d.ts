import type { UiHeader } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Page-level header: brand left, optional extras + user widget right.
 * Parity with {@code header.hbs}.
 *
 * Brand becomes an anchor when {@code brandHref} is set (so SPA navigation
 * and SSR reloads both work); the user widget always links to the profile.
 * Extras (e.g. a theme picker dropdown) recurse through the renderer so
 * apps can drop a {@link UiForm} or any other node in there.
 */
export declare function renderHeader(node: UiHeader, r: SuiRenderer): string;
