/*
 * Themes on the client: which one is on, and a picker in the header.
 *
 * Which theme is on is a decision about this browser, not about the account:
 * it changes no data, the server has no opinion on it, and two people sharing
 * a login should be able to disagree. So it lives entirely on the client —
 * localStorage plus a class on <html> — and nothing here talks to the server.
 *
 * Every theme listed here is an overlay on sui.css, scoped to its own class on
 * <html>: load the stylesheets once, and switching is a class swap with no
 * reload. sui-sbb.css is deliberately not in the list — it replaces sui.css
 * rather than layering on it, so it cannot be switched to by a class.
 *
 * The first paint is not this module's job. A module loads after the page is
 * drawn, and a theme applied that late flashes the previous one first; the
 * classic script theme-boot.js, included in <head>, sets the class before
 * anything is drawn. The two share STORAGE_KEY and must agree on the default.
 */

import { renderIcon } from "./renderers/icon.js";

/** A theme the picker offers. `id` is the part after `sui-theme-`. */
export interface SuiTheme {
    id: string;
    label: string;
    hint?: string;
}

export interface ThemeSwitchOptions {
    /** What the picker offers, in order. Defaults to {@link SUI_THEMES}. */
    themes?: SuiTheme[];
    /** The theme when nothing is remembered. Defaults to `"default"`. */
    defaultTheme?: string;
    /** Where the choice is remembered. Defaults to {@link STORAGE_KEY}. */
    storageKey?: string;
}

/** The localStorage key theme-boot.js reads too. */
export const STORAGE_KEY = "sui-theme";

/** The id that means "no theme class" — sui.css as it ships. */
export const DEFAULT_THEME = "default";

/** The themes that ship with the framework, as overlays on sui.css. */
export const SUI_THEMES: SuiTheme[] = [
    { id: "default",  label: "Default",  hint: "The framework's own" },
    { id: "dark",     label: "Dark",     hint: "The default, dark" },
    { id: "compact",  label: "Compact",  hint: "The default, tighter" },
    { id: "clody",    label: "Clody",    hint: "Warm, one plane" },
    { id: "gipiti",   label: "Gipiti",   hint: "Neutral, two planes" },
    { id: "sorbet",   label: "Sorbet",   hint: "Pastels, light" },
    { id: "amethyst", label: "Amethyst", hint: "Violet on a dark ground" },
];

const CLASS_PREFIX = "sui-theme-";

function resolve(options: ThemeSwitchOptions = {}) {
    return {
        themes: options.themes ?? SUI_THEMES,
        defaultTheme: options.defaultTheme ?? DEFAULT_THEME,
        storageKey: options.storageKey ?? STORAGE_KEY,
    };
}

/**
 * The theme this browser has chosen, or the default when nothing — or nothing
 * the list knows — is remembered.
 */
export function currentTheme(options: ThemeSwitchOptions = {}): string {
    const { themes, defaultTheme, storageKey } = resolve(options);
    let stored: string | null = null;
    try {
        stored = localStorage.getItem(storageKey);
    } catch {
        // Private mode, or storage disabled: the default it is.
    }
    return stored && themes.some(t => t.id === stored) ? stored : defaultTheme;
}

/**
 * Puts a theme on: swaps the class on <html> and remembers the choice. Any
 * other `sui-theme-*` class goes, so a theme the list does not name cannot
 * linger underneath the new one.
 */
export function applyTheme(theme: string, options: ThemeSwitchOptions = {}): void {
    const { storageKey } = resolve(options);
    const root = document.documentElement;
    Array.from(root.classList)
        .filter(c => c.startsWith(CLASS_PREFIX))
        .forEach(c => root.classList.remove(c));
    if (theme !== DEFAULT_THEME) root.classList.add(CLASS_PREFIX + theme);
    try {
        localStorage.setItem(storageKey, theme);
    } catch {
        // Not being remembered is survivable; the switch still works for this page.
    }
}

function escape(value: string): string {
    return value.replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);
}

/**
 * The picker, in exactly the markup a UiMenuButton renders, so it takes the
 * popover styling and the positioning enhancer for free. Only the click is
 * handled here, because choosing a theme is not a request.
 */
export function renderThemeSwitch(options: ThemeSwitchOptions = {}): HTMLElement {
    const { themes } = resolve(options);
    const active = currentTheme(options);
    const el = document.createElement("details");
    el.className = "sui-menu-button sui-menu-button--icon sui-menu-button--align-end sui-theme-switch";
    el.dataset.sui = "menu-button";
    el.innerHTML =
        '<summary class="sui-menu-button-trigger" role="button" aria-haspopup="menu"' +
        ' aria-expanded="false" aria-label="Theme">' +
        `<span class="sui-menu-button-glyph">${renderIcon("palette")}</span></summary>` +
        '<div class="sui-menu-button-popover" role="menu">' +
        themes.map(t =>
            '<button type="button" class="sui-menu-button-item" role="menuitemradio"' +
            ` aria-checked="${t.id === active}" data-theme="${escape(t.id)}">` +
            `<span class="sui-menu-button-item-label">${escape(t.label)}</span>` +
            (t.hint ? `<span class="sui-theme-switch-hint">${escape(t.hint)}</span>` : "") +
            "</button>").join("") +
        "</div>";

    el.addEventListener("click", event => {
        const item = (event.target as HTMLElement).closest<HTMLElement>("[data-theme]");
        if (!item) return;
        event.preventDefault();
        applyTheme(item.dataset.theme!, options);
        el.querySelectorAll("[data-theme]").forEach(b => {
            b.setAttribute("aria-checked", String(b === item));
        });
        el.open = false;   // the menu-button enhancer closes on a dispatch; there is none here
    });
    return el;
}

let observer: MutationObserver | null = null;

/**
 * Keeps a theme picker in the header, ahead of the user widget.
 *
 * The header is part of the server-rendered tree and comes back without the
 * picker after every navigation or patch that redraws the shell, so one
 * watcher on the document puts it back, coalesced into a single pass.
 * Calling this again replaces the options; it never installs a second watcher.
 */
export function installThemeSwitch(options: ThemeSwitchOptions = {}): void {
    if (typeof document === "undefined") return;

    const inject = () => {
        const right = document.querySelector(".sui-header .sui-header-right");
        if (!right || right.querySelector(".sui-theme-switch")) return;
        right.insertBefore(renderThemeSwitch(options), right.firstChild);
    };

    let scheduled = false;
    const schedule = () => {
        if (scheduled) return;
        scheduled = true;
        // A timeout rather than requestAnimationFrame: rAF does not run in a
        // background tab, and a page opened in one would come up without the
        // picker and stay that way until it was looked at.
        setTimeout(() => {
            scheduled = false;
            inject();
        }, 0);
    };

    observer?.disconnect();
    document.querySelectorAll(".sui-header .sui-theme-switch").forEach(el => el.remove());
    observer = new MutationObserver(schedule);
    observer.observe(document.documentElement, { childList: true, subtree: true });
    schedule();
}
