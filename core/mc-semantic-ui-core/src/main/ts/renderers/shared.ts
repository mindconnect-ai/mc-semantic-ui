import type { UiAction, UiLink, UiListItem, UiTrigger, Pagination } from "../model.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { renderAction } from "./action.js";
import { renderIcon } from "./icon.js";
import { renderLink } from "./link.js";

export function renderActions(actions: UiAction[]): string {
    return actions.map(renderAction).join("");
}

export function renderLinks(links: UiLink[]): string {
    return links.map(l => renderLink(l)).join("");
}

export function renderPagination(p: Pagination): string {
    const pages = Math.ceil(p.total / p.size);
    const prevDisabled = p.page <= 1;
    const nextDisabled = p.page >= pages;
    // Pagination buttons are regular triggers — the bus dispatches them
    // through APPLY_RESPONSE like any other action. The server provides a
    // trigger template with a literal {page} placeholder; we substitute it
    // per button below. Without a template, render plain disabled buttons
    // (informational only).
    const prevTrigger = p.pageTrigger ? triggerForPage(p.pageTrigger, p.page - 1) : null;
    const nextTrigger = p.pageTrigger ? triggerForPage(p.pageTrigger, p.page + 1) : null;
    return `<div class="sui-pagination">
        ${renderPageButton("‹ Prev", prevTrigger, prevDisabled)}
        <span>${p.page} / ${pages} (${p.total} total)</span>
        ${renderPageButton("Next ›", nextTrigger, nextDisabled)}
    </div>`;
}

function triggerForPage(template: UiTrigger, page: number): UiTrigger {
    return {
        ...template,
        url: (template.url ?? "").replace(/\{page\}/g, String(page)),
    };
}

function renderPageButton(label: string, trigger: UiTrigger | null, disabled: boolean): string {
    const dataTrigger = (trigger && !disabled) ? `data-trigger='${encodeTrigger(trigger)}'` : "";
    return `<button type="button" class="sui-btn sui-btn--secondary" ${dataTrigger} ${disabled ? "disabled" : ""}>${escapeHtml(label)}</button>`;
}

export function defaultRenderItem(item: UiListItem, r: SuiRenderer): string {
    // Rich label (labelNode) takes precedence over the plain text label; the
    // text label remains the fallback for accessibility / no-node rows.
    // A leading icon prefixes the plain label (not a rich labelNode, which
    // owns its own layout).
    const iconHtml = item.icon && !item.labelNode ? `${renderIcon(item.icon)} ` : "";
    const labelInner = item.labelNode ? r.render(item.labelNode) : `${iconHtml}${escapeHtml(item.label)}`;
    const label = item.onClick
        ? `<a class="sui-list-item-label" href="#" data-trigger='${encodeTrigger(item.onClick)}'>${labelInner}</a>`
        : `<span class="sui-list-item-label">${labelInner}</span>`;

    const body = item.content
        ? r.render(item.content)
        : item.description
            ? `<span class="sui-list-item-desc">${escapeHtml(item.description)}</span>`
            : "";

    const mainContent = `${label}${body}`;
    const summaryInner = item.collapseSummaryId
        ? `<span id="${escapeHtml(item.collapseSummaryId)}">${escapeHtml(item.collapseSummary)}</span>`
        : escapeHtml(item.collapseSummary);
    // Client-controlled collapse: render collapsed and tag the element so the
    // morpher won't clobber the user's manual open/close on later patches.
    const detailsAttrs = item.collapseClientControlled
        ? " data-sui-client-collapse"
        : (item.collapseOpen ? " open" : "");
    const wrappedContent = item.collapseSummary
        ? `<details class="sui-activity"${detailsAttrs}>
            <summary class="sui-activity-summary">${summaryInner}</summary>
            <div class="sui-activity-body">${mainContent}</div>
           </details>`
        : mainContent;

    return `<li class="sui-list-item" id="${escapeHtml(item.id)}" data-id="${escapeHtml(item.id)}">
        <div class="sui-list-item-main">${wrappedContent}</div>
        <div class="sui-list-item-actions">${renderActions(item.actions || [])}</div>
    </li>`;
}
