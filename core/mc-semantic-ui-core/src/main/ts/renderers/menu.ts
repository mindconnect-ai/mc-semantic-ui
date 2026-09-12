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
 *   <li>{@code rail} — icon-only; groups appear as fly-outs beside the rail
 *       (see {@link wireRailFlyouts});</li>
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
    // A fly-out belongs to the rail; leaving it must not strand one on screen.
    railFlyouts.get(menu)?.closeAll();
    if (persist && menu.id) {
        try { localStorage.setItem(STORAGE_PREFIX + menu.id, state); } catch { /* ignore */ }
    }
}

/** The user's saved choice for a menu, or null when there is none. */
function storedStateOf(menu: HTMLElement): MenuState | null {
    let stored: string | null = null;
    try { stored = localStorage.getItem(STORAGE_PREFIX + menu.id); } catch { /* ignore */ }
    return stored && MENU_STATES.includes(stored as MenuState) ? stored as MenuState : null;
}

/**
 * What a responsive menu should show for the CURRENT viewport. The saved
 * choice is a desktop choice: on a wide screen "expanded" is a sidebar beside
 * the content, on a narrow one the same word means a drawer lying across it.
 * So a narrow viewport always starts the drawer closed, whatever was saved,
 * and a wide one honours the choice.
 *
 * <p>Null means "nothing to apply": a wide viewport with no saved choice, where
 * the server-rendered state stands. The breakpoint watch passes a fallback
 * for that case — once the drawer has been closed there is no server state
 * left to keep, so widening again opens the sidebar.
 */
function viewportStateOf(menu: HTMLElement, wideFallback: MenuState | null = null): MenuState | null {
    return isMobile() ? "hidden" : (storedStateOf(menu) ?? wideFallback);
}

/**
 * Re-applies each menu's persisted state after a mount. Call once after
 * {@code renderer.mount(...)} so a user's collapse choice survives reloads.
 * Menus with no stored preference keep their server-rendered state — except a
 * responsive menu on a narrow screen, which starts closed regardless.
 *
 * <p>Also arms the breakpoint watch (once), so a window resized across the
 * drawer breakpoint after mount is handled too.
 */
export function restoreMenuState(root: ParentNode = document): void {
    root.querySelectorAll<HTMLElement>(".sui-menu[id]").forEach(menu => {
        if (menu.classList.contains("sui-menu--responsive")) {
            const state = viewportStateOf(menu);
            if (state) applyMenuState(menu, state, false);
            return;
        }
        const stored = storedStateOf(menu);
        if (stored) applyMenuState(menu, stored, false);
    });
    watchBreakpoint();
}

let breakpointWatched = false;

/**
 * Re-evaluates every responsive menu when the viewport crosses the drawer
 * breakpoint. Without this, a window narrowed after load kept its sidebar
 * "expanded" — which the stylesheet then drew as an absolutely positioned
 * drawer, open, on top of the page, with nothing to close it but the burger.
 *
 * <p>Registered once for the document: the media query outlives any page, and
 * the menus are looked up afresh on each change, so one mounted later is
 * covered as well. Nothing here is persisted — the window changed, not the
 * user's mind.
 */
function watchBreakpoint(): void {
    if (breakpointWatched || typeof matchMedia !== "function" || typeof document === "undefined") return;
    breakpointWatched = true;
    const query = matchMedia(MOBILE_QUERY);
    const onChange = (): void => {
        document.querySelectorAll<HTMLElement>(".sui-menu--responsive[id]").forEach(menu => {
            applyMenuState(menu, viewportStateOf(menu, "expanded") as MenuState, false);
        });
    };
    if (typeof query.addEventListener === "function") {
        query.addEventListener("change", onChange);
    } else if (typeof (query as MediaQueryList & { addListener?: (cb: () => void) => void }).addListener === "function") {
        // Safari before 14
        (query as MediaQueryList & { addListener: (cb: () => void) => void }).addListener(onChange);
    }
}

/** Test seam: forget that the breakpoint watch was armed. */
export function resetBreakpointWatchForTests(): void {
    breakpointWatched = false;
}

// ── Rail fly-outs ────────────────────────────────────────────────────────────

/** The gap between the rail and a fly-out or tooltip, in px. */
const FLYOUT_GAP = 8;
/** Keeps a fly-out on screen by at least this much, in px. */
const VIEWPORT_MARGIN = 8;
/** How long the pointer may be outside a group before its fly-out closes, in ms. */
const FLYOUT_CLOSE_DELAY = 150;

/** A rectangle as {@code getBoundingClientRect} reports it. */
export interface Box { top: number; left: number; right: number; bottom: number; }

/**
 * Where a fly-out (or tooltip) goes beside the rail item it belongs to, in
 * viewport coordinates for {@code position: fixed}. Beside the item on the
 * menu's open side, level with its top — or centred on it, for a tooltip —
 * and moved up when it would run past the bottom of the viewport.
 *
 * <p>Fixed, not absolute: the rail usually scrolls ({@code overflow-y: auto}
 * in an app shell), and a scroll container clips everything that sticks out of
 * it, sideways included. A fixed box is laid out against the viewport and is
 * not clipped by it.
 */
export function flyoutPosition(
    anchor: Box,
    size: { width: number; height: number },
    viewport: { width: number; height: number },
    side: "left" | "right",
    align: "top" | "center" = "top",
): { top: number; left: number } {
    const left = side === "right"
        ? anchor.left - FLYOUT_GAP - size.width
        : anchor.right + FLYOUT_GAP;
    const wanted = align === "center"
        ? anchor.top + (anchor.bottom - anchor.top - size.height) / 2
        : anchor.top;
    const lowest = viewport.height - VIEWPORT_MARGIN - size.height;
    return { top: Math.max(VIEWPORT_MARGIN, Math.min(wanted, lowest)), left };
}

const railFlyouts = new WeakMap<HTMLElement, RailFlyouts>();

/**
 * Makes a rail menu's submenus and tooltips reachable. Idempotent — the event
 * bus runs it after every render; call it yourself when rendering without one.
 *
 * <p>In the rail a group shows its items as a fly-out beside the icon, a leaf
 * its label as a tooltip. Both used to be absolutely positioned children of
 * the item, which a scrolling rail clips; a closed group ({@code <details>})
 * does not render its content at all; and the fly-out opened on hover only.
 * Once wired, the menu carries {@code data-sui-flyouts} and a fly-out:
 * <ul>
 *   <li>opens on hover, on keyboard focus, and on a tap or click of the group
 *       (which then no longer folds the group open or shut underneath);</li>
 *   <li>is placed with {@code position: fixed} (see {@link flyoutPosition}),
 *       so the rail can keep scrolling;</li>
 *   <li>stays open while the pointer crosses the gap to it, and closes when the
 *       pointer or focus has left the group, on Escape, on a click elsewhere,
 *       and when the rail scrolls or the window resizes.</li>
 * </ul>
 * A closed group is opened for as long as its fly-out shows and closed again
 * after, so the user's own open/closed choice for the expanded menu stands.
 */
export function wireRailFlyouts(root: ParentNode = document): void {
    root.querySelectorAll<HTMLElement>(".sui-menu").forEach(menu => {
        let flyouts = railFlyouts.get(menu);
        if (!flyouts) {
            flyouts = new RailFlyouts(menu);
            railFlyouts.set(menu, flyouts);
        }
        // Re-marked on every run: a re-render morphs attributes the markup
        // does not carry away, and the stylesheet keys its fallback on it.
        menu.setAttribute("data-sui-flyouts", "");
    });
}

class RailFlyouts {
    private open: { item: HTMLElement; panel: HTMLElement; openedDetails: HTMLDetailsElement | null } | null = null;
    private tip: HTMLElement | null = null;
    private closeTimer: ReturnType<typeof setTimeout> | null = null;
    /** Set while Escape hands focus back to the group header, which must not reopen the fly-out. */
    private returningFocus = false;

    constructor(private readonly menu: HTMLElement) {
        menu.addEventListener("pointerover", e => this.onPointerOver(e));
        menu.addEventListener("pointerout", e => this.onPointerOut(e));
        menu.addEventListener("focusin", e => this.onFocusIn(e));
        menu.addEventListener("focusout", e => this.onFocusOut(e));
        menu.addEventListener("click", e => this.onClick(e));
        menu.addEventListener("keydown", e => this.onKeyDown(e));
        menu.addEventListener("scroll", () => this.closeAll(), { passive: true });
        const doc = menu.ownerDocument;
        doc.addEventListener("pointerdown", e => {
            if (this.open && !this.open.item.contains(e.target as Node)) this.closeAll();
        });
        doc.defaultView?.addEventListener("resize", () => this.closeAll());
    }

    /** True while the menu shows as an icon rail — a responsive rail on a narrow screen is a full drawer. */
    private isRail(): boolean {
        return this.menu.classList.contains("sui-menu--rail")
            && !(this.menu.classList.contains("sui-menu--responsive") && isMobile());
    }

    /** The top-level item (a direct entry of the rail) an event happened in, if any. */
    private topItem(target: EventTarget | null): HTMLElement | null {
        const el = target instanceof Element ? target : null;
        const item = el?.closest<HTMLElement>(".sui-menu-list > .sui-menu-item") ?? null;
        return item && this.menu.contains(item) ? item : null;
    }

    private onPointerOver(e: PointerEvent): void {
        if (!this.isRail()) return;
        const item = this.topItem(e.target);
        if (!item) return;
        this.cancelClose();
        this.show(item);
    }

    private onPointerOut(e: PointerEvent): void {
        const item = this.topItem(e.target);
        if (!item || item.contains(e.relatedTarget as Node | null)) return;
        // Inside an open fly-out the item still contains the pointer (the
        // fly-out is its child, wherever it is drawn); leaving the item for the
        // gap starts a short grace period the fly-out cancels on arrival.
        this.scheduleClose();
    }

    private onFocusIn(e: FocusEvent): void {
        if (!this.isRail() || this.returningFocus) return;
        const item = this.topItem(e.target);
        if (!item) return;
        this.cancelClose();
        this.show(item);
    }

    private onFocusOut(e: FocusEvent): void {
        const item = this.topItem(e.target);
        if (item && !item.contains(e.relatedTarget as Node | null)) this.closeAll();
    }

    private onClick(e: MouseEvent): void {
        if (!this.isRail()) return;
        const target = e.target instanceof Element ? e.target : null;
        const summary = target?.closest<HTMLElement>(".sui-menu-list > .sui-menu-item--group > .sui-menu-group > summary");
        if (summary) {
            // In the rail the group header opens its fly-out; folding the
            // <details> underneath would only flip a state nobody can see.
            e.preventDefault();
            const item = summary.closest<HTMLElement>(".sui-menu-item")!;
            if (this.open?.item === item) this.closeAll(); else this.show(item);
            return;
        }
        // A choice made in a fly-out ends it.
        if (target?.closest(".sui-menu-sublist a, .sui-menu-sublist button")) this.closeAll();
    }

    private onKeyDown(e: KeyboardEvent): void {
        if (e.key !== "Escape" || !this.open) return;
        const summary = this.open.item.querySelector<HTMLElement>(":scope > .sui-menu-group > summary");
        this.closeAll();
        this.returningFocus = true;
        try { summary?.focus(); } finally { this.returningFocus = false; }
    }

    private show(item: HTMLElement): void {
        if (item.classList.contains("sui-menu-item--group")) this.showFlyout(item);
        else this.showTip(item);
    }

    private showFlyout(item: HTMLElement): void {
        if (this.open?.item === item) return;
        this.closeAll();
        const details = item.querySelector<HTMLDetailsElement>(":scope > .sui-menu-group");
        const panel = details?.querySelector<HTMLElement>(":scope > .sui-menu-sublist");
        if (!details || !panel) return;
        // A closed <details> does not render its content, whatever the CSS says.
        const openedDetails = details.open ? null : details;
        if (openedDetails) openedDetails.open = true;
        panel.classList.add("is-flyout-open");
        this.place(item, panel, "top");
        this.open = { item, panel, openedDetails };
        item.querySelector(":scope > .sui-menu-group > summary")?.setAttribute("aria-expanded", "true");
    }

    private showTip(item: HTMLElement): void {
        this.closeAll();
        const tip = item.querySelector<HTMLElement>(":scope > .sui-menu-tip");
        if (!tip) return;
        tip.classList.add("is-tip-open");
        this.place(item, tip, "center");
        this.tip = tip;
    }

    private place(item: HTMLElement, box: HTMLElement, align: "top" | "center"): void {
        this.placeNow(item, box, align);
        // Once more on the next frame: a <details> that has just been opened
        // for the fly-out lays out its content in the next rendering step,
        // and the first measurement can predate it.
        const view = this.menu.ownerDocument.defaultView;
        view?.requestAnimationFrame?.(() => {
            if (box.classList.contains("is-flyout-open") || box.classList.contains("is-tip-open")) {
                this.placeNow(item, box, align);
            }
        });
    }

    private placeNow(item: HTMLElement, box: HTMLElement, align: "top" | "center"): void {
        const view = this.menu.ownerDocument.defaultView;
        const pos = flyoutPosition(
            item.getBoundingClientRect(),
            { width: box.offsetWidth, height: box.offsetHeight },
            { width: view?.innerWidth ?? 0, height: view?.innerHeight ?? 0 },
            this.menu.classList.contains("sui-menu--right") ? "right" : "left",
            align);
        box.style.setProperty("--sui-flyout-top", `${pos.top}px`);
        box.style.setProperty("--sui-flyout-left", `${pos.left}px`);
    }

    private scheduleClose(): void {
        this.cancelClose();
        this.closeTimer = setTimeout(() => this.closeAll(), FLYOUT_CLOSE_DELAY);
    }

    private cancelClose(): void {
        if (this.closeTimer !== null) clearTimeout(this.closeTimer);
        this.closeTimer = null;
    }

    closeAll(): void {
        this.cancelClose();
        if (this.open) {
            const { item, panel, openedDetails } = this.open;
            panel.classList.remove("is-flyout-open");
            if (openedDetails) openedDetails.open = false;
            item.querySelector(":scope > .sui-menu-group > summary")?.removeAttribute("aria-expanded");
            this.open = null;
        }
        this.tip?.classList.remove("is-tip-open");
        this.tip = null;
    }
}
