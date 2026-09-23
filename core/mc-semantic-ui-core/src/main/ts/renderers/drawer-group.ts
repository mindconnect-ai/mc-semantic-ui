import type { UiDrawer, UiDrawerGroup } from "../model.js";
import type { OutlineHandler } from "../snapshot.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { cls } from "./util.js";

/**
 * Renders a {@link UiDrawerGroup}: several drawers at one edge, and the
 * rule for how they get on. The group is the element that stands at the
 * edge — over the area for OVERLAY, in the flow for PUSH — with the strip's
 * size; the drawers inside are ordinary drawer elements laid out by the
 * group's stylesheet instead of their own.
 *
 * <p>Each drawer is rendered with the group's edge, scope and mode written
 * over its own, so the classes on it agree with where the group put it. The
 * wiring (open, minimize, close, the handle) is the drawer's own and works
 * unchanged; the group adds one thing, bringing a stacked drawer to the front
 * ({@code stackFront} in drawer.ts).
 *
 * <p>No server template yet: the server renders a group as a placeholder.
 */
export function renderDrawerGroup(node: UiDrawerGroup, r: SuiRenderer): string {
    const id = escapeHtml(node.id);
    const edge = (node.edge ?? "RIGHT").toLowerCase();
    const scope = (node.scope ?? "VIEWPORT").toLowerCase();
    const mode = (node.mode ?? "OVERLAY").toLowerCase();
    const arrange = (node.arrange ?? "SHARE").toLowerCase();
    const resizable = node.resizable ? " sui-drawer-group--resizable" : "";
    const vars = [["size", node.size], ["min", node.minSize], ["max", node.maxSize]]
        .filter(([, v]) => v != null && CSS_LENGTH.test(v))
        .map(([k, v]) => `--sui-drawer-${k}:${v};`).join("");
    const style = vars ? ` style="${vars}"` : "";
    const active = node.active ? ` data-active="${escapeHtml(node.active)}"` : "";
    const activeTrigger = node.onActiveChange ? ` data-active-trigger='${encodeTrigger(node.onActiveChange)}'` : "";
    // The group's edge, scope and mode are the drawers' too. A drawer's own
    // resize grip makes no sense inside a strip whose size is the group's.
    const drawers = (node.drawers ?? [])
        .map(d => r.render({ ...d, edge: node.edge ?? "RIGHT", scope: node.scope ?? "VIEWPORT", mode: node.mode ?? "OVERLAY", resizable: false } as UiDrawer))
        .join("");
    const resize = node.resizable
        ? `<div class="sui-drawer-resize" data-sui-drawer="resize" role="separator" aria-orientation="${edge === "top" || edge === "bottom" ? "horizontal" : "vertical"}" aria-label="Resize"></div>`
        : "";
    return `<div class="${cls(`sui-drawer-group sui-drawer-group--${edge} sui-drawer-group--${scope} sui-drawer-group--${mode} sui-drawer-group--${arrange}${resizable}`, node)}" id="${id}" data-sui="drawer-group"${active}${activeTrigger}${style}>`
        + drawers + resize + `</div>`;
}

/** A CSS length, as UiDrawerGroup checks it on the server. */
const CSS_LENGTH = /^\d{1,5}(?:\.\d{1,3})?(?:px|rem|em|vh|vw|dvh|svh|lvh|%)$/;

/**
 * What a group says about itself: which drawer is in front (STACK), read off
 * the element because the user changes it in the browser, and the drawers,
 * each describing itself.
 */
export const outlineDrawerGroup: OutlineHandler<UiDrawerGroup> = (node, ctx) => ({
    title: node.title,
    state: ctx.attr(node.id, "data-active") ?? undefined,
    children: node.drawers ?? [],
});
