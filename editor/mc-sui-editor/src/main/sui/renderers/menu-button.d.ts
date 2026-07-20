import type { UiMenuButton } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Renders a {@link UiMenuButton}: a trigger that opens a floating dropdown /
 * context menu of {@link UiMenuItem}s.
 *
 * <p>Structure is a native {@code <details>} so it works with no JS at all — the
 * summary toggles the popover open/closed. When the SPA event bus is loaded it
 * intercepts the summary, drives the open/close itself, and re-positions the
 * popover with {@code position: fixed} so it escapes any scrolling / overflow
 * ancestor (a tree, a table cell, an overlay shell). See {@code wireMenuButton}
 * / the click + keydown handlers in {@code eventbus.ts}.
 *
 * <p>Mirrors the SSR {@code menu-button.hbs} template — same markup, so a menu
 * rendered on the server and one built in the browser are indistinguishable.
 */
export declare function renderMenuButton(node: UiMenuButton, r: SuiRenderer): string;
/**
 * Installs the document-level listeners that turn every {@link UiMenuButton}'s
 * native {@code <details>} into a click-positioned popover (open/close, outside-
 * click, Escape, close-on-scroll/resize). Idempotent — safe to call after every
 * render; menus added later by a patch are handled by the same delegated
 * listeners with no re-wiring.
 */
export declare function wireMenuButtons(_root?: ParentNode): void;
