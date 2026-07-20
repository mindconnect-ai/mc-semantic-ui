import { escapeHtml, encodeTrigger } from "../renderer.js";
import type { UiTrigger } from "../model.js";

export function cls(base: string, node: { cssClass?: string }): string {
    return node.cssClass ? `${base} ${escapeHtml(node.cssClass)}` : base;
}

/**
 * Renders a node's event triggers as {@code data-sui-on-<event>} attributes
 * — the generic "this node reacts to a DOM event" mechanism every node type
 * inherits from {@code UiNode}.
 *
 * <p>Returns a leading-space attribute string (or ""), so renderers splice it
 * straight into their root tag next to the class attribute:
 *
 * <pre>{@code
 * `<div class="${cls("sui-stack", node)}"${evt(node)} id="…">`
 * }</pre>
 *
 * <p>Pass event names to {@code skip} when the renderer already emits its own
 * attribute for them — an action's click is carried by {@code data-trigger}
 * (which also drives the no-JS anchor/form), so emitting both would put the
 * same trigger on the element twice.
 *
 * <p>Parity: the SSR side emits the same attributes via the {@code events}
 * Handlebars helper. The event bus delegates from the root, so an element
 * carrying these attributes behaves identically whether it came from
 * Handlebars or from here.
 */
export function evt(node: NodeEvents, ...skip: string[]): string {
    const all: Array<[string, UiTrigger | undefined]> = [
        ["click",    node.onClick],
        ["dblclick", node.onDblClick],
        ["hover",    node.onHover],
        ["leave",    node.onLeave],
        ["change",   node.onChange],
        ["input",    node.onInput],
    ];
    return all
        .filter(([name, trigger]) => !!trigger && !skip.includes(name))
        .map(([name, trigger]) => ` data-sui-on-${name}='${encodeTrigger(trigger!)}'`)
        .join("");
}

interface NodeEvents {
    onClick?: UiTrigger; onDblClick?: UiTrigger; onHover?: UiTrigger;
    onLeave?: UiTrigger; onChange?: UiTrigger; onInput?: UiTrigger;
}
