import type { UiSection } from "../model.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { cls, evt } from "./util.js";

export function renderSection(node: UiSection, r: SuiRenderer): string {
    // Collapsible sections wrap the regular section body in a <details>
    // disclosure. We recurse with collapseSummary cleared so the inner body
    // renders as normal tab/stack layout.
    if (node.collapseSummary) {
        const body = renderSectionBody({ ...node, collapseSummary: undefined }, r);
        return `<details class="sui-section--collapsible"${node.collapseOpen ? " open" : ""} id="${escapeHtml(node.id)}">
            <summary class="sui-section-summary">${escapeHtml(node.collapseSummary)}</summary>
            ${body}
        </details>`;
    }
    return renderSectionBody(node, r);
}

function renderSectionBody(node: UiSection, r: SuiRenderer): string {
    // UiSection is a tabbed container: every entry becomes a tab, one panel
    // is visible. For plain composition (header + body stacked) use UiStack.
    //
    // Special case "stack mode": every entry is title-less AND has no
    // href. That's the shape the chat session sends (messages-panel +
    // input-form panel under one parent section, neither named). Rendering
    // it as tabs would produce empty buttons and hide every panel except
    // the first — which manifests as "no chat input shown". Detect that
    // shape and emit all panels visible.
    const titleHtml = node.title ? `<h2>${escapeHtml(node.title)}</h2>` : "";
    const stackOnly = node.sections.length > 0
        && node.sections.every(s => !s.title && !s.href);
    if (stackOnly) {
        const panels = node.sections.map(s =>
            `<div class="sui-panel" id="${escapeHtml(s.id)}">${r.render(s.content)}</div>`
        ).join("");
        return `<div class="${cls("sui-section", node)}"${evt(node)} id="${escapeHtml(node.id)}">
            ${titleHtml}
            <div class="sui-panels">${panels}</div>
        </div>`;
    }

    const activeId = node.initialSection || node.sections[0]?.id;
    const tabs = node.sections.map(s => {
        const activeCls = s.id === activeId ? " active" : "";
        const icon = s.icon ? `${renderIcon(s.icon)} ` : "";
        const tabLabel = `${icon}${escapeHtml(s.title ?? "")}`;
        // Optional click trigger, fired alongside the panel switch (e.g. lazy-load).
        const trigAttr = s.onClick ? ` data-trigger='${encodeTrigger(s.onClick)}'` : "";
        if (s.href) {
            // SSR-friendly tab: real anchor. The EventBus intercepts the
            // click via data-href when SPA is active.
            return `<a class="sui-tab${activeCls}" href="${escapeHtml(s.href)}" data-href="${escapeHtml(s.href)}" data-target="${escapeHtml(s.id)}"${noSelect(s)}${trigAttr}>${tabLabel}</a>`;
        }
        return `<button class="sui-tab${activeCls}" data-target="${escapeHtml(s.id)}"${noSelect(s)}${trigAttr}>${tabLabel}</button>`;
    }).join("");
    const panels = node.sections.map(s =>
        `<div class="sui-panel" id="${escapeHtml(s.id)}" ${s.id !== activeId ? "hidden" : ""}>${r.render(s.content)}</div>`
    ).join("");

    // MENU overflow: the bar keeps a single row and wireTabOverflow() moves the
    // tabs that don't fit into a "⋯" dropdown. data-overflow marks it; with no
    // JS the CSS still wraps, so the fallback stays usable.
    const overflowAttr = node.tabOverflow === "MENU"
        ? ` data-sui-overflow="menu" data-sui-overflow-items=".sui-tab"`
        : "";
    return `<div class="${cls("sui-section", node)}" id="${escapeHtml(node.id)}">
        ${titleHtml}
        <nav class="sui-tabs"${overflowAttr}>${tabs}</nav>
        <div class="sui-panels">${panels}</div>
    </div>`;
}

/**
 * `selectOnClick: false` → the bus fires the entry's trigger but leaves the
 * panel alone, so the application can decide whether the tab may change.
 */
function noSelect(entry: { selectOnClick?: boolean }): string {
    return entry.selectOnClick === false ? " data-sui-no-select" : "";
}
