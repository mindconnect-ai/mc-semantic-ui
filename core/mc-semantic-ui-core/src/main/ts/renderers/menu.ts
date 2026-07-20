import type { UiMenu, UiMenuItem } from "../model.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { cls, evt } from "./util.js";

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
export const MENU_STATES: MenuState[] = ["expanded", "rail", "hidden"];

const STORAGE_PREFIX = "sui-menu:";

export function renderMenu(node: UiMenu, r: SuiRenderer): string {
    const state = (node.state || "EXPANDED").toLowerCase();
    const mode = (node.mode || "PUSH").toLowerCase();
    const side = (node.side || "LEFT").toLowerCase();
    const showToggle = node.toggle !== false;
    const id = escapeHtml(node.id);
    const items = (node.items || []).map(i => renderChild(i, r)).join("");
    const toggle = showToggle
        ? `<button type="button" class="sui-menu-toggle" data-menu-toggle="${id}" aria-label="Toggle menu" aria-expanded="${state !== "hidden"}">${renderIcon("menu")}</button>`
        : "";
    const title = node.title
        ? `<span class="sui-menu-title">${escapeHtml(node.title)}</span>`
        : "";
    const nav = `<nav class="${cls(`sui-menu sui-menu--${state} sui-menu--${mode} sui-menu--${side}`, node)}"${evt(node)} id="${id}" data-sui="menu" data-menu-state="${state}">
        <div class="sui-menu-head">${toggle}${title}</div>
        <ul class="sui-menu-list" role="menu">${items}</ul>
    </nav>`;
    // Overlay (and responsive, when it drops to a drawer on small screens)
    // float over the content; a sibling backdrop dims the rest of the
    // (position:relative) shell region and closes on click.
    const backdrop = (mode === "overlay" || mode === "responsive")
        ? `<div class="sui-menu-backdrop" data-menu-close="${id}"></div>`
        : "";
    return nav + backdrop;
}

/** Dispatches a child through the registry (lenient for type-less legacy JSON). */
function renderChild(node: UiMenuItem, r: SuiRenderer): string {
    return node.type ? r.render(node) : renderMenuItem(node, r);
}

/** Renders one menu entry (a {@code <li>}). Registered under {@code "menu-item"}. */
export function renderMenuItem(node: UiMenuItem, r: SuiRenderer): string {
    const id = escapeHtml(node.id);
    const icon = `<span class="sui-menu-icon">${node.icon ? renderIcon(node.icon) : ""}</span>`;
    const label = `<span class="sui-menu-label">${escapeHtml(node.label ?? "")}</span>`;
    const badge = node.badge
        ? `<span class="sui-menu-badge">${escapeHtml(node.badge)}</span>`
        : "";
    const activeCls = node.selected ? " is-active" : "";
    const children = node.children || [];

    if (children.length > 0) {
        const sub = children.map(c => renderChild(c, r)).join("");
        return `<li class="sui-menu-item sui-menu-item--group${activeCls}" id="${id}" data-id="${id}" role="none">
            <details class="sui-menu-group" data-sui-client-collapse${node.open ? " open" : ""}>
                <summary class="sui-menu-link" role="menuitem">${icon}${label}${badge}<span class="sui-menu-caret">${renderIcon("chevron-down")}</span></summary>
                <ul class="sui-menu-sublist" role="menu">${sub}</ul>
            </details>
        </li>`;
    }

    // Leaf: an anchor. onClick → dispatched via the bus (data-trigger); else a
    // plain navigation (data-href hint + real href for no-JS).
    const href = escapeHtml(node.href ?? "#");
    const nav = node.onClick
        ? `data-trigger='${encodeTrigger(node.onClick)}'`
        : `data-href="${href}"`;
    const confirm = node.onClick && node.confirm ? ` data-confirm="${escapeHtml(node.confirm)}"` : "";
    const current = node.selected ? ` aria-current="page"` : "";
    // A tooltip label shown beside the icon when the rail is collapsed.
    const tip = `<span class="sui-menu-tip">${escapeHtml(node.label ?? "")}</span>`;
    return `<li class="sui-menu-item${activeCls}" id="${id}" data-id="${id}" role="none">
        <a class="sui-menu-link" href="${href}" ${nav}${confirm}${current} role="menuitem">${icon}${label}${badge}</a>${tip}
    </li>`;
}

// ── Client-side state machine (used by the event bus + restore-on-load) ──────

/** Next state in the expanded → rail → hidden → expanded cycle. */
export function cycleMenuState(current: MenuState): MenuState {
    const i = MENU_STATES.indexOf(current);
    return MENU_STATES[(i + 1) % MENU_STATES.length];
}

/** The narrow-viewport breakpoint below which a responsive menu is a drawer. */
const MOBILE_QUERY = "(max-width: 768px)";
function isMobile(): boolean {
    return typeof matchMedia === "function" && matchMedia(MOBILE_QUERY).matches;
}

/**
 * The state a hamburger click should move a given menu to. For a responsive
 * menu the toggle is a two-way switch whose meaning depends on the viewport:
 * on a wide screen it flips expanded ⇄ rail (the sidebar never fully vanishes);
 * on a narrow one it flips the drawer open ⇄ closed (expanded ⇄ hidden). Every
 * other menu uses the full three-state {@link cycleMenuState}.
 */
export function nextMenuState(menu: HTMLElement): MenuState {
    const current = menuStateOf(menu);
    if (menu.classList.contains("sui-menu--responsive")) {
        return isMobile()
            ? (current === "hidden" ? "expanded" : "hidden")
            : (current === "rail" ? "expanded" : "rail");
    }
    return cycleMenuState(current);
}

/** The current state read from a menu element's {@code data-menu-state}. */
export function menuStateOf(menu: HTMLElement): MenuState {
    const s = menu.dataset.menuState as MenuState | undefined;
    return s && MENU_STATES.includes(s) ? s : "expanded";
}

/**
 * Applies a state to a menu element: swaps the modifier class, updates
 * {@code data-menu-state} + the toggle's {@code aria-expanded}, and (when a
 * Storage is available) persists the choice under {@code sui-menu:<id>}.
 */
export function applyMenuState(menu: HTMLElement, state: MenuState, persist = true): void {
    for (const s of MENU_STATES) menu.classList.toggle(`sui-menu--${s}`, s === state);
    menu.dataset.menuState = state;
    const toggle = menu.querySelector<HTMLElement>(".sui-menu-toggle");
    if (toggle) toggle.setAttribute("aria-expanded", String(state !== "hidden"));
    if (persist && menu.id) {
        try { localStorage.setItem(STORAGE_PREFIX + menu.id, state); } catch { /* ignore */ }
    }
}

/**
 * Re-applies each menu's persisted state after a mount. Call once after
 * {@code renderer.mount(...)} so a user's collapse choice survives reloads.
 * Menus with no stored preference keep their server-rendered state.
 */
export function restoreMenuState(root: ParentNode = document): void {
    root.querySelectorAll<HTMLElement>(".sui-menu[id]").forEach(menu => {
        let stored: string | null = null;
        try { stored = localStorage.getItem(STORAGE_PREFIX + menu.id); } catch { /* ignore */ }
        if (stored && MENU_STATES.includes(stored as MenuState)) {
            applyMenuState(menu, stored as MenuState, false);
            return;
        }
        // No saved preference: a responsive menu starts closed on a small
        // screen (a drawer behind the burger) and open on a wide one.
        if (menu.classList.contains("sui-menu--responsive") && isMobile()) {
            applyMenuState(menu, "hidden", false);
        }
    });
}
