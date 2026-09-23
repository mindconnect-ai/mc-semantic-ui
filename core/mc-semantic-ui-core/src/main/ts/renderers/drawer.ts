import type { UiDrawer } from "../model.js";
import type { OutlineHandler } from "../snapshot.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { cls } from "./util.js";

/**
 * Renders a {@link UiDrawer}: a panel that slides in from an edge, with a
 * handle to bring it back when it is minimized. Mirrors `drawer.hbs`.
 *
 * Open, minimized and closed are classes on the one element; switching
 * between them ({@link setDrawerState}) never redraws the content, so what
 * was typed and a widget's own state survive a minimize.
 */
export function renderDrawer(node: UiDrawer, r: SuiRenderer): string {
    const id = escapeHtml(node.id);
    const title = escapeHtml(node.title ?? "");
    const edge = (node.edge ?? "RIGHT").toLowerCase();
    const state = (node.state ?? "OPEN").toLowerCase();
    const scope = (node.scope ?? "VIEWPORT").toLowerCase();
    const mode = (node.mode ?? "OVERLAY").toLowerCase();
    const resizable = node.resizable ? " sui-drawer--resizable" : "";
    // The sizes reach a style attribute: a CSS length or nothing (UiDrawer
    // refuses anything else on the server; this checks again).
    const vars = [["size", node.size], ["min", node.minSize], ["max", node.maxSize]]
        .filter(([, v]) => v != null && CSS_LENGTH.test(v))
        .map(([k, v]) => `--sui-drawer-${k}:${v};`).join("");
    const style = vars ? ` style="${vars}"` : "";
    const stateTrigger = node.onStateChange ? ` data-state-trigger='${encodeTrigger(node.onStateChange)}'` : "";
    const closeTrigger = node.onClose ? ` data-close-trigger='${encodeTrigger(node.onClose)}'` : "";
    const icon = node.icon ? `<span class="sui-drawer-icon">${renderIcon(node.icon)}</span>` : "";
    const badge = node.badge ? `<span class="sui-drawer-badge">${escapeHtml(node.badge)}</span>` : "";
    const close = node.closable
        ? `<button type="button" class="sui-drawer-btn" data-sui-drawer="close" aria-label="Close" title="Close">${renderIcon("x")}</button>`
        : "";
    const resize = node.resizable
        ? `<div class="sui-drawer-resize" data-sui-drawer="resize" role="separator" aria-orientation="${edge === "top" || edge === "bottom" ? "horizontal" : "vertical"}" aria-label="Resize"></div>`
        : "";
    return `<div class="${cls(`sui-drawer sui-drawer--${edge} sui-drawer--${state} sui-drawer--${scope} sui-drawer--${mode}${resizable}`, node)}" id="${id}" data-sui="drawer" data-state="${state}"${stateTrigger}${closeTrigger}${style}>`
        + `<button type="button" class="sui-drawer-handle" data-sui-drawer="open" aria-controls="${id}__panel" aria-expanded="${state === "open"}">${icon}<span class="sui-drawer-title">${title}</span>${badge}</button>`
        + `<div class="sui-drawer-panel" id="${id}__panel" role="region" aria-label="${title}" tabindex="-1">`
        + `<div class="sui-drawer-header">${icon}<span class="sui-drawer-title">${title}</span>`
        + `<button type="button" class="sui-drawer-btn" data-sui-drawer="minimize" aria-label="Minimize" title="Minimize">${renderIcon("minus")}</button>${close}</div>`
        + `<div class="sui-drawer-body">${node.content ? r.render(node.content) : ""}</div>${resize}</div></div>`;
}

/** A CSS length, as UiDrawer.CSS_LENGTH checks it on the server. */
const CSS_LENGTH = /^\d{1,5}(?:\.\d{1,3})?(?:px|rem|em|vh|vw|dvh|svh|lvh|%)$/;

export type DrawerState = "open" | "minimized" | "closed";

/** Told about every change the user makes, to fire `onStateChange` / `onClose`. */
export type DrawerListener = (drawer: HTMLElement, state: DrawerState, closedByUser: boolean) => void;

/** Told when the user brings another drawer of a STACK group to the front. */
export type StackListener = (group: HTMLElement, drawer: HTMLElement) => void;

/**
 * Moves a drawer to {@code state} — a class swap, nothing redrawn — and, with
 * {@code focus}, takes the focus along: into the panel when it opens, back to
 * the handle when it is minimized. Returns whether anything changed.
 */
export function setDrawerState(drawer: HTMLElement, state: DrawerState, focus = false): boolean {
    const before = (drawer.dataset.state ?? "open") as DrawerState;
    if (before === state) return false;
    drawer.classList.remove(`sui-drawer--${before}`);
    drawer.classList.add(`sui-drawer--${state}`);
    drawer.setAttribute("data-state", state);
    const handle = drawer.querySelector<HTMLElement>(":scope > .sui-drawer-handle");
    handle?.setAttribute("aria-expanded", String(state === "open"));
    if (focus) {
        if (state === "open") {
            const panel = drawer.querySelector<HTMLElement>(":scope > .sui-drawer-panel");
            (firstFocusable(panel) ?? panel)?.focus();
        } else if (state === "minimized") {
            handle?.focus();
        }
    }
    layoutDrawerHandles();
    return true;
}

/** The first control in a drawer's body a user can type in or press — not a hidden or disabled one. */
function firstFocusable(panel: HTMLElement | null): HTMLElement | null {
    if (!panel) return null;
    const candidates = panel.querySelectorAll<HTMLElement>(
        ".sui-drawer-body input, .sui-drawer-body textarea, .sui-drawer-body select, .sui-drawer-body button, "
        + ".sui-drawer-body [contenteditable='true'], .sui-drawer-body [tabindex]");
    for (const el of Array.from(candidates)) {
        if ((el as HTMLInputElement).disabled || el.getAttribute("tabindex") === "-1") continue;
        if (el.tagName === "INPUT" && (el as HTMLInputElement).type === "hidden") continue;
        return el;
    }
    return null;
}

/**
 * Lines up the handles of the minimized drawers that share an edge and a
 * container, side by side, in document order: each gets the room the ones
 * before it took as `--sui-drawer-offset`.
 */
export function layoutDrawerHandles(scope: ParentNode | null = typeof document !== "undefined" ? document : null): void {
    if (!scope || typeof scope.querySelectorAll !== "function") return;
    layoutStacks(scope);
    // Per container (null: the window), per edge: the room taken so far.
    const taken = new Map<Element | null, Map<string, number>>();
    for (const drawer of Array.from(scope.querySelectorAll<HTMLElement>(".sui-drawer--minimized.sui-drawer--overlay"))) {
        const edge = edgeOf(drawer);
        const host = drawer.classList.contains("sui-drawer--container") ? drawer.parentElement : null;
        let byEdge = taken.get(host);
        if (!byEdge) taken.set(host, byEdge = new Map());
        const offset = byEdge.get(edge) ?? 0;
        drawer.style.setProperty("--sui-drawer-offset", `${offset}px`);
        const handle = drawer.querySelector<HTMLElement>(":scope > .sui-drawer-handle");
        const extent = handle ? (edge === "left" || edge === "right" ? handle.offsetHeight : handle.offsetWidth) : 0;
        byEdge.set(edge, offset + extent + 8);
    }
}

/**
 * The other open overlay drawers at the same edge of the same area as
 * {@code drawer} — the container it is in, or the window — and not in a
 * group, which has its own rule for its members.
 */
function othersOpenAtEdge(drawer: HTMLElement): HTMLElement[] {
    const parent = drawer.parentElement;
    if (!parent || parent.classList.contains("sui-drawer-group")) return [];
    if (!drawer.classList.contains("sui-drawer--overlay")) return [];
    const edge = edgeOf(drawer);
    const inContainer = drawer.classList.contains("sui-drawer--container");
    const scope: ParentNode | null = inContainer ? parent : (typeof document !== "undefined" ? document : null);
    if (!scope || typeof scope.querySelectorAll !== "function") return [];
    return Array.from(scope.querySelectorAll<HTMLElement>(".sui-drawer--open.sui-drawer--overlay")).filter(d =>
        d !== drawer && edgeOf(d) === edge
        && d.classList.contains("sui-drawer--container") === inContainer
        && (!inContainer || d.parentElement === parent)
        && !d.parentElement?.classList.contains("sui-drawer-group"));
}

/** The edge a drawer element slides in from, read off its class. */
function edgeOf(drawer: HTMLElement): string {
    return drawer.getAttribute("class")?.match(/sui-drawer(?:-group)?--(top|bottom|left|right)\b/)?.[1] ?? "right";
}

/**
 * In every STACK group, marks the drawer in front: the one {@code data-active}
 * names when it is open, otherwise the first open one. Runs with the handle
 * layout, so a minimize or a patch that changes what is open settles it.
 */
function layoutStacks(scope: ParentNode): void {
    for (const group of Array.from(scope.querySelectorAll<HTMLElement>(".sui-drawer-group--stack"))) {
        const all = Array.from(group.querySelectorAll<HTMLElement>(":scope > .sui-drawer"));
        const open = all.filter(d => d.classList.contains("sui-drawer--open"));
        const wanted = group.getAttribute("data-active");
        const front = open.find(d => d.id === wanted) ?? open[0] ?? null;
        // Every drawer, not only the open ones: a drawer minimized from the
        // front must lose the mark, or reopening it would find it "already in
        // front" and bring nothing forward.
        for (const d of all) d.classList.toggle("sui-drawer--front", d === front);
        if (front && front.id !== wanted) group.setAttribute("data-active", front.id);
    }
}

/**
 * Brings a drawer of a STACK group to the front — a class swap, nothing
 * redrawn — and tells the group's {@code onActiveChange}. Returns whether
 * anything changed.
 */
export function stackFront(drawer: HTMLElement): boolean {
    const group = drawer.parentElement;
    if (!group?.classList.contains("sui-drawer-group--stack")) return false;
    if (drawer.classList.contains("sui-drawer--front")) return false;
    for (const d of Array.from(group.querySelectorAll<HTMLElement>(":scope > .sui-drawer"))) {
        d.classList.toggle("sui-drawer--front", d === drawer);
    }
    group.setAttribute("data-active", drawer.id);
    return true;
}

let wired = false;
let listener: DrawerListener | null = null;
let stackListener: StackListener | null = null;

/**
 * Wires every drawer on the page, once: the handle opens, the header's
 * buttons minimize and close, Escape inside an open drawer minimizes it, and
 * the resize grip drags its size. Idempotent; drawers added later by a patch
 * are covered by the same delegated listeners. {@code onChange} hears of
 * every change the user makes.
 */
export function wireDrawers(onChange?: DrawerListener, onFront?: StackListener): void {
    if (onChange) listener = onChange;
    if (onFront) stackListener = onFront;
    if (typeof document === "undefined") return;
    if (!wired) {
        wired = true;
        document.addEventListener("click", onClick);
        document.addEventListener("keydown", onKeydown);
        document.addEventListener("pointerdown", onPointerDown);
    }
    layoutDrawerHandles(document);
}

function change(drawer: HTMLElement, state: DrawerState, focus: boolean, closedByUser = false): void {
    if (!setDrawerState(drawer, state, focus)) return;
    listener?.(drawer, state, closedByUser);
    // One open drawer per edge of an area: the one opened now lies over any
    // other open there, whose own handle is hidden while it is open — so it
    // could neither be seen nor brought back. It goes to its handle instead.
    // Two that should be open together belong in a group, which lays them
    // out; a PUSH drawer takes room of its own and covers nothing.
    if (state === "open") {
        for (const other of othersOpenAtEdge(drawer)) change(other, "minimized", false);
    }
    // A drawer opened from its handle in a stack is the one the user wants to
    // see; one minimized from the front leaves the next open one in front.
    const group = drawer.parentElement;
    if (group?.classList.contains("sui-drawer-group--stack")) {
        if (state === "open" && stackFront(drawer)) stackListener?.(group, drawer);
        else layoutStacks(group.parentElement ?? group);
    }
}

function onClick(e: MouseEvent): void {
    const target = e.target as HTMLElement | null;
    const control = target?.closest?.<HTMLElement>("[data-sui-drawer]");
    const drawer = control?.closest<HTMLElement>(".sui-drawer");
    if (!control || !drawer) {
        // The header bar of a stacked drawer that is not in front: pressing
        // it brings the drawer forward. Its buttons still do their own thing.
        const header = target?.closest?.<HTMLElement>(".sui-drawer-header");
        const stacked = header?.closest<HTMLElement>(".sui-drawer");
        const inStack = stacked?.parentElement?.classList.contains("sui-drawer-group--stack");
        if (inStack && stacked && stacked.classList.contains("sui-drawer--open") && stackFront(stacked)) {
            e.preventDefault();
            stackListener?.(stacked.parentElement as HTMLElement, stacked);
        }
        return;
    }
    switch (control.getAttribute("data-sui-drawer")) {
        case "open":     e.preventDefault(); change(drawer, "open", true); break;
        case "minimize": e.preventDefault(); change(drawer, "minimized", true); break;
        case "close":    e.preventDefault(); change(drawer, "closed", false, true); break;
    }
}

/** Escape in an open drawer minimizes it (an open menu inside takes Escape first). */
function onKeydown(e: KeyboardEvent): void {
    if (e.key !== "Escape" || e.defaultPrevented) return;
    const target = e.target as HTMLElement | null;
    const drawer = target?.closest?.<HTMLElement>(".sui-drawer--open");
    if (!drawer || !target?.closest?.(".sui-drawer-panel")) return;
    if (drawer.querySelector("details.sui-menu-button[open]")) return;
    e.preventDefault();
    change(drawer, "minimized", true);
}

/**
 * Dragging the grip on the inner edge sets the size; the stylesheet keeps it
 * within min/max.
 *
 * <p>Measured as a movement, not as a position: the size it started at plus
 * how far the pointer has come, along the axis the edge grows in. The earlier
 * reading — the distance from the window's edge, or from the container's —
 * assumed the drawer was flush against that edge. An overlay drawer is; one
 * in {@code PUSH} mode is a box in the layout, and anything standing beside
 * it moves its edge somewhere else entirely, so the first small drag made it
 * jump by however far those two edges were apart.
 */
function onPointerDown(e: PointerEvent): void {
    const grip = (e.target as HTMLElement | null)?.closest?.<HTMLElement>("[data-sui-drawer='resize']");
    // The grip belongs to the nearest drawer or group; a group's own grip is
    // its direct child, a drawer's sits inside the drawer's panel.
    const drawer = grip?.closest<HTMLElement>(".sui-drawer, .sui-drawer-group");
    if (!grip || !drawer) return;
    e.preventDefault();
    const edge = edgeOf(drawer);
    // What carries the size: the whole element when it takes room in the
    // layout or is a group's strip, the panel when it floats above it.
    const sized = drawer.classList.contains("sui-drawer--push") || drawer.classList.contains("sui-drawer-group")
        ? drawer
        : drawer.querySelector<HTMLElement>(".sui-drawer-panel") ?? drawer;
    const box = sized.getBoundingClientRect();
    const startSize = edge === "top" || edge === "bottom" ? box.height : box.width;
    const startX = e.clientX;
    const startY = e.clientY;
    drawer.classList.add("is-resizing");
    const move = (ev: PointerEvent): void => {
        const grown = edge === "bottom" ? startY - ev.clientY
            : edge === "top" ? ev.clientY - startY
            : edge === "left" ? ev.clientX - startX
            : startX - ev.clientX;
        drawer.style.setProperty("--sui-drawer-size", `${Math.max(0, Math.round(startSize + grown))}px`);
    };
    const up = (): void => {
        drawer.classList.remove("is-resizing");
        document.removeEventListener("pointermove", move);
        document.removeEventListener("pointerup", up);
    };
    document.addEventListener("pointermove", move);
    document.addEventListener("pointerup", up);
}

/**
 * For a REPLACE: every drawer in {@code node} that does not say which state
 * it wants takes the one its element has now — the user minimized it, and a
 * server redrawing the page did not mean to undo that.
 */
export function keepDrawerStates<N>(node: N, lookup: (id: string) => HTMLElement | null): N {
    const visit = (value: unknown): unknown => {
        if (Array.isArray(value)) return value.map(visit);
        if (value == null || typeof value !== "object") return value;
        const record = value as Record<string, unknown>;
        let out: Record<string, unknown> | null = null;
        for (const [k, v] of Object.entries(record)) {
            const nv = visit(v);
            if (nv !== v) (out ??= { ...record })[k] = nv;
        }
        const result = out ?? record;
        if (result.type === "drawer" && result.state == null && typeof result.id === "string") {
            const live = lookup(result.id)?.getAttribute("data-state");
            if (live === "open" || live === "minimized" || live === "closed") {
                return { ...result, state: live.toUpperCase() };
            }
        }
        // The same for who is in front of a stack: the user's choice stands
        // until the server names another.
        if (result.type === "drawer-group" && result.active == null && typeof result.id === "string") {
            const live = lookup(result.id)?.getAttribute("data-active");
            if (live) return { ...result, active: live };
        }
        return result;
    };
    return visit(node) as N;
}

/**
 * What a drawer says about itself. Its state is the one thing the model
 * cannot tell: opening and minimizing happen in the browser without the
 * server hearing about it, so it is read off the element.
 */
export const outlineDrawer: OutlineHandler<UiDrawer> = (node, ctx) => ({
    title: node.title,
    state: ctx.attr(node.id, "data-state") ?? undefined,
    children: node.content ? [node.content] : [],
});
