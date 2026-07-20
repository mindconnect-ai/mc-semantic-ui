import type { UiMenu, UiMenuItem } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Renders a {@link UiMenu}: a collapsible vertical navigation sidebar.
 *
 * <p>Three states, reflected as a modifier class + {@code data-menu-state} on
 * the root:
 * <ul>
 *   <li>{@code expanded} — icon + label, groups expand inline;</li>
 *   <li>{@code rail} — icon-only; groups appear as hover fly-outs (pure CSS);</li>
 *   <li>{@code hidden} — off-canvas, only the hamburger shows.</li>
 * </ul>
 *
 * <p>The hamburger carries {@code data-menu-toggle="<id>"}; the event bus
 * intercepts it, cycles the state and persists it (see {@link cycleMenuState} /
 * {@link applyMenuState}). Groups are native {@code <details>} carrying
 * {@code data-sui-client-collapse} so the morpher preserves the user's manual
 * open/close across re-renders — the same mechanism the tree uses.
 */
export type MenuState = "expanded" | "rail" | "hidden";
export declare const MENU_STATES: MenuState[];
export declare function renderMenu(node: UiMenu, r: SuiRenderer): string;
/** Renders one menu entry (a {@code <li>}). Registered under {@code "menu-item"}. */
export declare function renderMenuItem(node: UiMenuItem, r: SuiRenderer): string;
/** Next state in the expanded → rail → hidden → expanded cycle. */
export declare function cycleMenuState(current: MenuState): MenuState;
/**
 * The state a hamburger click should move a given menu to. For a responsive
 * menu the toggle is a two-way switch whose meaning depends on the viewport:
 * on a wide screen it flips expanded ⇄ rail (the sidebar never fully vanishes);
 * on a narrow one it flips the drawer open ⇄ closed (expanded ⇄ hidden). Every
 * other menu uses the full three-state {@link cycleMenuState}.
 */
export declare function nextMenuState(menu: HTMLElement): MenuState;
/** The current state read from a menu element's {@code data-menu-state}. */
export declare function menuStateOf(menu: HTMLElement): MenuState;
/**
 * Applies a state to a menu element: swaps the modifier class, updates
 * {@code data-menu-state} + the toggle's {@code aria-expanded}, and (when a
 * Storage is available) persists the choice under {@code sui-menu:<id>}.
 */
export declare function applyMenuState(menu: HTMLElement, state: MenuState, persist?: boolean): void;
/**
 * Re-applies each menu's persisted state after a mount. Call once after
 * {@code renderer.mount(...)} so a user's collapse choice survives reloads.
 * Menus with no stored preference keep their server-rendered state.
 */
export declare function restoreMenuState(root?: ParentNode): void;
