import type { UiAppShell } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Application frame: header on top, menu beside the content, optional footer
 * across the bottom. Parity with {@code app-shell.hbs}.
 *
 * <p>The node's whole point is that the error-prone parts are handled here
 * instead of by the caller:
 * <ul>
 *   <li>the header's burger is pointed at the menu's id, and the menu's own
 *       toggle is switched off — one burger, not two;</li>
 *   <li>the shell fills the viewport by default, so the sidebar reaches the
 *       bottom of the window and the content scrolls inside it;</li>
 *   <li>the content area is a <em>slot</em> ({@code data-sui-slot}), so a
 *       REPLACE patch aimed at it fills it instead of deleting the container.</li>
 * </ul>
 * Header and menu are copied before those adjustments — the caller's nodes are
 * never mutated, which matters because the same menu object is usually reused
 * across pages.
 */
export declare function renderAppShell(node: UiAppShell, r: SuiRenderer): string;
