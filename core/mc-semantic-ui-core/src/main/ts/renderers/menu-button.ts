import type { UiActionMenu, UiMenuButton, UiMenuItem } from "../model.js";
import type { OutlineHandler } from "../snapshot.js";
import { describeAction } from "./action.js";
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

/**
 * Renders a {@link UiActionMenu}: a button that opens a menu, where a button
 * goes — a form's button bar. The same `<details>` a {@link UiMenuButton} is,
 * so the same wiring opens, positions and drives it; the trigger wears the
 * button classes, so it looks like its neighbours. Mirrors `action-menu.hbs`.
 */
export function renderActionMenu(node: UiActionMenu): string {
    const id = escapeHtml(node.id ?? "");
    const style = (node.style ?? "SECONDARY").toLowerCase();
    const extra = node.cssClass ? " " + escapeHtml(node.cssClass) : "";
    const busy = node.loading ? " is-loading" : "";
    const disabled = node.loading || node.enabled === false ? ` aria-disabled="true"` : "";
    const title = escapeHtml(node.disabledReason || node.label || "");
    const icon = node.icon ? `<span class="sui-menu-button-glyph">${renderIcon(node.icon)}</span>` : "";
    const items = (node.items || []).map(i => renderMenuButtonItem(i)).join("");
    return `<details class="sui-menu-button sui-menu-button--action sui-menu-button--align-start${extra}" id="${id}" data-sui="menu-button">`
        + `<summary class="sui-menu-button-trigger sui-btn sui-btn--${style}${busy}" role="button" aria-haspopup="menu" aria-expanded="false"${disabled} title="${title}">`
        + `${icon}<span class="sui-menu-button-text">${escapeHtml(node.label ?? "")}</span><span class="sui-menu-button-caret">${renderIcon("chevron-down")}</span></summary>`
        + `<div class="sui-menu-button-popover" role="menu">${items}</div></details>`;
}

/** One popover entry: a separator, a heading, a nesting group (submenu), or a leaf. */
function renderMenuButtonItem(node: UiMenuItem): string {
    if (node.divider) {
        return `<div class="sui-menu-button-sep" role="separator"></div>`;
    }
    // A label over the entries that follow — not an item, not focusable.
    if (node.heading) {
        return `<div class="sui-menu-button-heading" role="presentation">${escapeHtml(node.label ?? "")}</div>`;
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
        const off = node.enabled === false
            ? " disabled" + (node.disabledReason ? ` title="${escapeHtml(node.disabledReason)}"` : "")
            : "";
        return `<button type="button" class="sui-menu-button-item${dangerCls}" id="${id}" data-id="${id}" role="menuitem" data-trigger='${encodeTrigger(node.onClick)}'${confirm}${off}>${icon}${label}${badge}</button>`;
    }
    // Disabled: a link to nowhere — no href, so neither the browser nor the
    // bus goes anywhere, and the keyboard passes it by.
    if (node.enabled === false) {
        const title = node.disabledReason ? ` title="${escapeHtml(node.disabledReason)}"` : "";
        return `<a class="sui-menu-button-item${dangerCls}" id="${id}" data-id="${id}" role="menuitem" aria-disabled="true"${title}>${icon}${label}${badge}</a>`;
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
    // A long menu scrolls itself; that scroll must not close it.
    document.addEventListener("scroll", e => {
        if ((e.target as Element | null)?.closest?.(".sui-menu-button-popover")) return;
        closeAllMenuButtons();
    }, true);
}

function onDocumentClick(e: MouseEvent): void {
    const target = e.target as HTMLElement | null;
    if (!target) return;

    const trigger = target.closest<HTMLElement>(".sui-menu-button-trigger");
    if (trigger) {
        const details = trigger.closest<HTMLDetailsElement>("details.sui-menu-button");
        if (details) {
            e.preventDefault();   // suppress the native <details> toggle; we drive it
            if (trigger.getAttribute("aria-disabled") === "true") return;
            // Enter / Space were already handled on keydown; the click some
            // browsers still synthesise from them must not toggle it back.
            if (e.detail === 0 && keyToggled === details) { keyToggled = null; return; }
            if (details.open) closeMenuButton(details);
            else {
                closeAllMenuButtons();
                openMenuButton(details);
                // Enter / Space on the trigger arrive as a click with no
                // pointer (detail 0): carry the focus into the menu, so the
                // arrow keys go on from there.
                if (e.detail === 0) focusEntry(menuEntries(popoverOf(details)), 0);
            }
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

/** The popover of a menu-button. */
function popoverOf(details: HTMLElement): HTMLElement | null {
    return details.querySelector<HTMLElement>(":scope > .sui-menu-button-popover");
}

/**
 * The entries of one menu level the keyboard moves between: its items and
 * submenu headers, not its dividers, headings or disabled items.
 */
function menuEntries(list: HTMLElement | null): HTMLElement[] {
    if (!list) return [];
    return Array.from(list.querySelectorAll<HTMLElement>(
        ":scope > .sui-menu-button-item, :scope > .sui-menu-button-group > .sui-menu-button-item--group"))
        .filter(el => !(el as HTMLButtonElement).disabled && el.getAttribute("aria-disabled") !== "true");
}

/** Focuses entry {@code index} of {@code entries}; negative counts from the end. */
function focusEntry(entries: HTMLElement[], index: number): void {
    if (entries.length === 0) return;
    const i = ((index % entries.length) + entries.length) % entries.length;
    entries[i].focus();
}

/**
 * The keyboard, as a menu has it: ↓/↑ on the trigger open the menu at the
 * first/last entry; inside, ↓/↑ move (wrapping), Home/End jump, → opens a
 * submenu and ← goes back out of one, Tab leaves and closes, Escape closes and
 * returns to the trigger. Enter / Space choose, as they press any button.
 */
/** The menu Enter / Space just toggled on keydown — see onDocumentClick. */
let keyToggled: HTMLDetailsElement | null = null;

function onDocumentKeydown(e: KeyboardEvent): void {
    const target = e.target as HTMLElement | null;
    const onTrigger = target?.closest?.<HTMLElement>(".sui-menu-button-trigger");
    // Enter / Space on the trigger: handled here rather than left to the
    // browser, whose activation of a <summary> differs from one to the next.
    if (onTrigger && (e.key === "Enter" || e.key === " ") && !e.repeat) {
        const details = onTrigger.closest<HTMLDetailsElement>("details.sui-menu-button");
        if (!details) return;
        e.preventDefault();
        if (onTrigger.getAttribute("aria-disabled") === "true") return;
        keyToggled = details;
        setTimeout(() => { if (keyToggled === details) keyToggled = null; }, 0);
        if (details.open) { closeMenuButton(details); return; }
        closeAllMenuButtons();
        openMenuButton(details);
        focusEntry(menuEntries(popoverOf(details)), 0);
        return;
    }
    if (onTrigger && (e.key === "ArrowDown" || e.key === "ArrowUp")) {
        const details = onTrigger.closest<HTMLDetailsElement>("details.sui-menu-button");
        if (!details || onTrigger.getAttribute("aria-disabled") === "true") return;
        e.preventDefault();
        if (!details.open) { closeAllMenuButtons(); openMenuButton(details); }
        focusEntry(menuEntries(popoverOf(details)), e.key === "ArrowDown" ? 0 : -1);
        return;
    }
    const level = target?.closest?.<HTMLElement>(".sui-menu-button-submenu, .sui-menu-button-popover");
    const inOpenMenu = level?.closest("details.sui-menu-button[open]");
    if (target && level && inOpenMenu && e.key !== "Escape") {
        const entries = menuEntries(level);
        const at = entries.indexOf(target);
        switch (e.key) {
            case "ArrowDown": e.preventDefault(); focusEntry(entries, at + 1); return;
            case "ArrowUp":   e.preventDefault(); focusEntry(entries, at < 0 ? -1 : at - 1); return;
            case "Home":      e.preventDefault(); focusEntry(entries, 0); return;
            case "End":       e.preventDefault(); focusEntry(entries, -1); return;
            case "ArrowRight": {
                const group = target.closest<HTMLElement>(".sui-menu-button-group");
                if (!group || !target.classList.contains("sui-menu-button-item--group")) return;
                e.preventDefault();
                group.classList.add("is-open");
                target.setAttribute("aria-expanded", "true");
                focusEntry(menuEntries(group.querySelector<HTMLElement>(":scope > .sui-menu-button-submenu")), 0);
                return;
            }
            case "ArrowLeft": {
                if (!level.classList.contains("sui-menu-button-submenu")) return;
                e.preventDefault();
                const group = level.closest<HTMLElement>(".sui-menu-button-group");
                const header = group?.querySelector<HTMLElement>(":scope > .sui-menu-button-item--group");
                group?.classList.remove("is-open");
                header?.setAttribute("aria-expanded", "false");
                header?.focus();
                return;
            }
            case "Tab":
                closeMenuButton(inOpenMenu as HTMLDetailsElement);   // the focus moves on as usual
                return;
        }
        return;
    }
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

    // Below when it fits; above when it does not and does there (a button bar
    // fixed to the bottom of a dialog); otherwise on the roomier side, cut to
    // the room and scrolling — never cut off by the window.
    const below = window.innerHeight - margin - (r.bottom + gap);
    const above = r.top - gap - margin;
    let top: number;
    pop.style.maxHeight = "";
    pop.style.overflowY = "";
    if (ph <= below) {
        top = r.bottom + gap;
    } else if (ph <= above) {
        top = r.top - gap - ph;
    } else if (above > below) {
        pop.style.maxHeight = `${Math.floor(above)}px`;
        pop.style.overflowY = "auto";
        top = margin;
    } else {
        pop.style.maxHeight = `${Math.floor(below)}px`;
        pop.style.overflowY = "auto";
        top = r.bottom + gap;
    }

    pop.style.left = `${Math.round(left)}px`;
    pop.style.top = `${Math.round(top)}px`;
    pop.style.visibility = "";
}

/**
 * A menu is a button and a list of entries. It describes itself as the
 * button; its entries are pressable in their own right, so they are handed
 * back as entries and stand beside it in the snapshot rather than under it.
 */
export const outlineActionMenu: OutlineHandler<UiActionMenu> = (node, ctx) => ({
    kind: "action",
    action: describeAction(node, ctx),
    entries: node.items ?? [],
});

/** The same for a standalone menu button. */
export const outlineMenuButton: OutlineHandler<UiMenuButton> = (node, ctx) => ({
    kind: "action",
    action: describeAction({ id: node.id, label: node.label ?? node.title ?? "", icon: node.icon }, ctx),
    entries: node.items ?? [],
});

/**
 * One entry. A heading or a divider is not pressable and carries no id worth
 * reporting; a group is a button with its children beside it, like the menu
 * itself.
 */
export const outlineMenuItem: OutlineHandler<UiMenuItem> = (node, ctx) => {
    if (node.heading) return { label: node.label };
    if (node.divider) return {};
    const action = describeAction(node, ctx);
    if (node.badge) action.label = `${action.label ?? ""} (${node.badge})`;
    return { kind: "action", action, entries: node.children ?? [] };
};
