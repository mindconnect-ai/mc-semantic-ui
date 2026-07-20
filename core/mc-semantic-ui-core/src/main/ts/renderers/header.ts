import type { UiHeader } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { cls, evt } from "./util.js";

/**
 * Page-level header: brand left, optional extras + user widget right.
 * Parity with {@code header.hbs}.
 *
 * Brand becomes an anchor when {@code brandHref} is set (so SPA navigation
 * and SSR reloads both work); the user widget always links to the profile.
 * Extras (e.g. a theme picker dropdown) recurse through the renderer so
 * apps can drop a {@link UiForm} or any other node in there.
 */
export function renderHeader(node: UiHeader, r: SuiRenderer): string {
    const idAttr = node.id ? ` id="${escapeHtml(node.id)}"` : "";
    // Leading hamburger that toggles a named menu (data-menu-toggle is handled
    // by the event bus, targeting the menu by id). Moves the burger into the top bar.
    const burger = node.menuToggle
        ? `<button type="button" class="sui-menu-toggle sui-header-burger" data-menu-toggle="${escapeHtml(node.menuToggle)}" aria-label="Toggle menu">${renderIcon("menu")}</button>`
        : "";
    const logo = node.brandLogo
        ? `<img class="sui-header-logo" src="${escapeHtml(node.brandLogo)}" alt="${escapeHtml(node.brand)}">`
        : "";
    const brand = node.brandHref
        ? `<a class="sui-header-brand" href="${escapeHtml(node.brandHref)}" data-href="${escapeHtml(node.brandHref)}">${logo}${escapeHtml(node.brand)}</a>`
        : `<span class="sui-header-brand">${logo}${escapeHtml(node.brand)}</span>`;
    // The shared overflow behaviour (wireOverflow) picks this up; no item
    // selector needed, every extra may move into the dropdown.
    const overflowAttr = node.extrasOverflow === "MENU" ? ` data-sui-overflow="menu"` : "";
    const extras = (node.extras && node.extras.length > 0)
        ? `<div class="sui-header-extras"${overflowAttr}>${node.extras.map(e => r.render(e)).join("")}</div>`
        : "";
    const user = node.user
        ? `<a class="sui-header-user" href="${escapeHtml(node.user.profileHref ?? "#")}"${node.user.profileHref ? ` data-href="${escapeHtml(node.user.profileHref)}"` : ""} title="${escapeHtml(node.user.name)}">
            <span class="sui-header-avatar">${escapeHtml(node.user.initials)}</span>
            <span class="sui-header-username">${escapeHtml(node.user.name)}</span>
          </a>`
        : "";
    return `<header class="${cls("sui-header", node)}"${evt(node)}${idAttr}>${burger}${brand}<div class="sui-header-right">${extras}${user}</div></header>`;
}
