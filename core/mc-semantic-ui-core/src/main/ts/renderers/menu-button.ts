import type { UiMenuButton, UiMenuItem } from "../model.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { cls, evt } from "./util.js";

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
export function renderMenuButton(node: UiMenuButton, r: SuiRenderer): string {
    const id = escapeHtml(node.id ?? "");
    const align = (node.align ?? "END").toLowerCase();
    const hasLabel = node.label != null && node.label !== "";
    const variant = (node.variant ?? (hasLabel ? "BUTTON" : "ICON")).toLowerCase();
    const iconToken = node.icon ?? "more";

    const icon = `<span class="sui-menu-button-glyph">${renderIcon(iconToken)}</span>`;
    const label = hasLabel
        ? `<span class="sui-menu-button-text">${escapeHtml(node.label!)}</span>` +
          `<span class="sui-menu-button-caret">${renderIcon("chevron-down")}</span>`
        : "";
    const aria = hasLabel ? "" : ` aria-label="${escapeHtml(node.title ?? "Menu")}"`;

    const items = (node.items || []).map(i => renderMenuButtonItem(i)).join("");

    return `<details class="${cls(`sui-menu-button sui-menu-button--${variant} sui-menu-button--align-${align}`, node)}"${evt(node)} id="${id}" data-sui="menu-button">
        <summary class="sui-menu-button-trigger" role="button" aria-haspopup="menu" aria-expanded="false"${aria}>${icon}${label}</summary>
        <div class="sui-menu-button-popover" role="menu">${items}</div>
    </details>`;
}

/** One popover entry: a separator, a nesting group (submenu), or a leaf. */
function renderMenuButtonItem(node: UiMenuItem): string {
    if (node.divider) {
        return `<div class="sui-menu-button-sep" role="separator"></div>`;
    }
    const id = escapeHtml(node.id ?? "");
    const icon = `<span class="sui-menu-button-item-icon">${node.icon ? renderIcon(node.icon) : ""}</span>`;
    const label = `<span class="sui-menu-button-item-label">${escapeHtml(node.label ?? "")}</span>`;
    const badge = node.badge
        ? `<span class="sui-menu-button-item-badge">${escapeHtml(node.badge)}</span>`
        : "";
    const dangerCls = node.danger ? " is-danger" : "";
    const children = node.children || [];

    // A group (has children) → a submenu. Same UiMenuItem.children the sidebar
    // menu uses for nesting. On the desktop CSS flies the sub-popover out to the
    // side on hover / focus; on touch the SPA toggles an `.is-open` class on
    // click (see wireMenuButtons). The sub-popover is positioned away from the
    // aligned edge so it doesn't run off-screen.
    if (children.length > 0) {
        const sub = children.map(c => renderMenuButtonItem(c)).join("");
        const caret = `<span class="sui-menu-button-submenu-caret">${renderIcon("chevron-right")}</span>`;
        return `<div class="sui-menu-button-group" id="${id}" data-id="${id}">` +
            `<button type="button" class="sui-menu-button-item sui-menu-button-item--group${dangerCls}" role="menuitem" aria-haspopup="menu" aria-expanded="false">${icon}${label}${badge}${caret}</button>` +
            `<div class="sui-menu-button-submenu" role="menu">${sub}</div>` +
            `</div>`;
    }

    // onClick → dispatch via the bus (data-trigger); else navigate (real href
    // for no-JS + data-href hint for the SPA router). A pure trigger item is a
    // <button>; a navigating one is an <a> so it works without JS.
    if (node.onClick) {
        const confirm = node.confirm ? ` data-confirm="${escapeHtml(node.confirm)}"` : "";
        return `<button type="button" class="sui-menu-button-item${dangerCls}" id="${id}" data-id="${id}" role="menuitem" data-trigger='${encodeTrigger(node.onClick)}'${confirm}>${icon}${label}${badge}</button>`;
    }
    const href = escapeHtml(node.href ?? "#");
    return `<a class="sui-menu-button-item${dangerCls}" id="${id}" data-id="${id}" role="menuitem" href="${href}" data-href="${href}">${icon}${label}${badge}</a>`;
}

// ── Open / close / position (SPA) ────────────────────────────────────────────
//
// A menu-button is a native <details> so it opens with no JS. When the SPA is
// present we take over: a delegated click drives open/close and, crucially, we
// re-position the popover with position:fixed at open time so it is never
// clipped by a scrolling / overflow-hidden ancestor (a tree, a table cell, the
// app shell). Item clicks still dispatch through the event bus's own listener
// (the items carry data-trigger / data-href); we only close the popover after.
// Mirrors wireTabOverflow: apps call this once after mount.

let wired = false;

/**
 * Installs the document-level listeners that turn every {@link UiMenuButton}'s
 * native {@code <details>} into a click-positioned popover (open/close, outside-
 * click, Escape, close-on-scroll/resize). Idempotent — safe to call after every
 * render; menus added later by a patch are handled by the same delegated
 * listeners with no re-wiring.
 */
export function wireMenuButtons(_root: ParentNode = document): void {
    if (wired || typeof document === "undefined") return;
    wired = true;
    document.addEventListener("click", onDocumentClick);
    document.addEventListener("keydown", onDocumentKeydown);
    // A popover positioned to the viewport must not drift when the page scrolls
    // or resizes; closing is the simplest correct response. Capture-phase catches
    // scrolls in nested containers too.
    window.addEventListener("resize", closeAllMenuButtons);
    document.addEventListener("scroll", closeAllMenuButtons, true);
}

function onDocumentClick(e: MouseEvent): void {
    const target = e.target as HTMLElement | null;
    if (!target) return;

    const trigger = target.closest<HTMLElement>(".sui-menu-button-trigger");
    if (trigger) {
        const details = trigger.closest<HTMLDetailsElement>("details.sui-menu-button");
        if (details) {
            e.preventDefault();   // suppress the native <details> toggle; we drive it
            if (details.open) closeMenuButton(details);
            else { closeAllMenuButtons(); openMenuButton(details); }
        }
        return;
    }

    // A group header (has a submenu): toggle it open (for touch / click; the
    // desktop also opens it on hover via CSS). Leave the outer popover open.
    const groupHeader = target.closest<HTMLElement>(".sui-menu-button-item--group");
    if (groupHeader) {
        const group = groupHeader.closest<HTMLElement>(".sui-menu-button-group");
        if (group) {
            const open = group.classList.toggle("is-open");
            groupHeader.setAttribute("aria-expanded", String(open));
        }
        return;
    }

    // A leaf item chosen: the bus dispatches it (separate listener); we close.
    const item = target.closest<HTMLElement>(".sui-menu-button-item");
    if (item) {
        const details = item.closest<HTMLDetailsElement>("details.sui-menu-button");
        if (details) closeMenuButton(details);
        return;
    }

    // A click anywhere outside a menu-button closes any that are open.
    if (!target.closest(".sui-menu-button")) closeAllMenuButtons();
}

function onDocumentKeydown(e: KeyboardEvent): void {
    if (e.key !== "Escape") return;
    const open = document.querySelector<HTMLDetailsElement>("details.sui-menu-button[open]");
    if (!open) return;
    closeAllMenuButtons();
    open.querySelector<HTMLElement>(".sui-menu-button-trigger")?.focus();
}

function closeAllMenuButtons(): void {
    document.querySelectorAll<HTMLDetailsElement>("details.sui-menu-button[open]").forEach(closeMenuButton);
}

function closeMenuButton(details: HTMLDetailsElement): void {
    details.open = false;
    details.querySelector<HTMLElement>(".sui-menu-button-trigger")?.setAttribute("aria-expanded", "false");
    // Collapse any expanded submenus so the menu reopens in its initial shape.
    details.querySelectorAll<HTMLElement>(".sui-menu-button-group.is-open").forEach(g => {
        g.classList.remove("is-open");
        g.querySelector<HTMLElement>(".sui-menu-button-item--group")?.setAttribute("aria-expanded", "false");
    });
    // Drop the fixed-position inline styles so a later keyboard open falls back
    // to the stylesheet's absolute positioning cleanly.
    const pop = details.querySelector<HTMLElement>(".sui-menu-button-popover");
    if (pop) pop.removeAttribute("style");
}

function openMenuButton(details: HTMLDetailsElement): void {
    details.open = true;
    const trigger = details.querySelector<HTMLElement>(".sui-menu-button-trigger");
    trigger?.setAttribute("aria-expanded", "true");
    const pop = details.querySelector<HTMLElement>(".sui-menu-button-popover");
    if (trigger && pop) {
        positionPopover(trigger, pop, details.classList.contains("sui-menu-button--align-start"));
        // Decide which way submenus should fly out: right by default, left when
        // the popover sits too close to the right edge to fit one. Keyed off the
        // popover's real position, not its alignment (a right-aligned menu can
        // still be near the left of the viewport).
        const rect = pop.getBoundingClientRect();
        const SUBMENU_W = 200;
        details.classList.toggle("sui-menu-button--submenu-left", rect.right + SUBMENU_W > window.innerWidth - 8);
    }
}

/**
 * Pins the popover to the viewport just below (or above) the trigger, aligned to
 * its start/end edge, flipping up when there's no room below and clamping into
 * the viewport. position:fixed means no ancestor's overflow can clip it.
 */
function positionPopover(trigger: HTMLElement, pop: HTMLElement, alignStart: boolean): void {
    const gap = 6, margin = 8;
    const r = trigger.getBoundingClientRect();
    // Measure at a neutral spot first so offsetWidth/Height are the natural size.
    pop.style.position = "fixed";
    pop.style.visibility = "hidden";
    pop.style.right = "auto";
    pop.style.bottom = "auto";
    pop.style.top = "0px";
    pop.style.left = "0px";
    const pw = pop.offsetWidth, ph = pop.offsetHeight;

    let left = alignStart ? r.left : r.right - pw;
    left = Math.max(margin, Math.min(left, window.innerWidth - pw - margin));

    let top = r.bottom + gap;
    if (top + ph > window.innerHeight - margin && r.top - gap - ph > margin) {
        top = r.top - gap - ph;   // flip above the trigger
    }
    top = Math.max(margin, Math.min(top, window.innerHeight - ph - margin));

    pop.style.left = `${Math.round(left)}px`;
    pop.style.top = `${Math.round(top)}px`;
    pop.style.visibility = "";
}
