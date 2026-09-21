import type { UiIcon } from "../model.js";

/**
 * Icon rendering — deliberately behind a swappable resolver so the icon
 * library is not baked into the model or the renderers.
 *
 * <p>UiNodes only ever carry a **token** (e.g. {@code "delete"}); what that
 * token becomes is decided here. The default resolver emits an SVG
 * {@code <use>} that references the curated sprite shipped at
 * {@code /sui/icons.svg} (and mirrored on the CDN). Apps that want a
 * different sprite, inline SVG, or an icon font call
 * {@link setIconResolver}.
 */
export type IconOpts = {
    /** Extra CSS classes on the icon element (e.g. a color/size modifier). */
    cssClass?: string;
    /**
     * Accessible label. When set, the icon is exposed to assistive tech
     * ({@code role="img"} + {@code <title>}); when absent the icon is
     * decorative ({@code aria-hidden}).
     */
    title?: string;
    /**
     * DOM id for the icon element. Set for a standalone {@link UiIcon} node
     * so a patch can REPLACE/REMOVE it; omitted for decorative leading icons.
     */
    id?: string;
};

/** Turns an icon token into HTML. Swappable via {@link setIconResolver}. */
export type IconResolver = (name: string, opts: IconOpts) => string;

// The sprite lives next to the compiled bundle: renderers/icon.js →
// ../icons.svg = /sui/icons.svg. Resolving against import.meta.url means the
// same code works when served from a Spring app (/sui/…) and from jsDelivr
// (…/sui/…) with zero configuration.
const DEFAULT_SPRITE_URL = new URL("../icons.svg", import.meta.url).href;

function esc(s: string): string {
    return String(s).replace(/[&<>"']/g, c =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));
}

/**
 * The default resolver: one {@code <svg><use></svg>} into a sprite. Size is
 * {@code 1em} (via {@code .sui-icon} CSS) and color is {@code currentColor},
 * so an icon inherits the surrounding text's size and color.
 */
export function spriteIconResolver(spriteUrl: string): IconResolver {
    return (name, opts) => {
        const cls = "sui-icon" + (opts.cssClass ? " " + esc(opts.cssClass) : "");
        const idAttr = opts.id ? ` id="${esc(opts.id)}"` : "";
        const a11y = opts.title
            ? `role="img" aria-label="${esc(opts.title)}"`
            : `aria-hidden="true"`;
        const titleEl = opts.title ? `<title>${esc(opts.title)}</title>` : "";
        return `<svg${idAttr} class="${cls}" ${a11y}>${titleEl}<use href="${esc(spriteUrl)}#${esc(name)}"></use></svg>`;
    };
}

// ── Icon sets ──────────────────────────────────────────────────────────────
// A plugin brings its own icons as a sprite for a prefix: every token that
// starts with `brand-` comes from the brand sprite, every other one from the
// standard sprite. The asset registry's installAll() adds the icon sets it
// knows before the first render; the longest matching prefix wins, so
// `acme-logo-` can sit inside `acme-`.

/** Lowercase-kebab ending in `-`: `brand-`, `acme-logo-`. */
const PREFIX_RE = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*-$/;
/** The class an icon from a set carries besides {@code sui-icon}. */
const SET_CLASS = "sui-icon--set";

let fallbackSpriteUrl = DEFAULT_SPRITE_URL;
/** The icon sets, longest prefix first. */
let iconSprites: { prefix: string; url: string }[] = [];

function iconSetFor(name: string): { prefix: string; url: string } | undefined {
    return iconSprites.find(set => name.startsWith(set.prefix));
}

/** The sprite a token comes from: its longest matching icon set, else the standard sprite. */
export function spriteUrlFor(name: string): string {
    return iconSetFor(name)?.url ?? fallbackSpriteUrl;
}

/**
 * Resolves every token to a {@code <use>} into the sprite {@link spriteUrlFor}
 * picks — the default resolver, and the one {@link getIconResolver} returns
 * until an app installs its own. An icon from a set also gets the class
 * {@code sui-icon--set}: its symbols draw with fills (a fixed colour, or
 * {@code currentColor} to follow the text) rather than the standard icons'
 * strokes. Mirrors IconRenderer on the server.
 */
const prefixedSpriteResolver: IconResolver = (name, opts) => {
    const set = iconSetFor(name);
    if (!set) return spriteIconResolver(fallbackSpriteUrl)(name, opts);
    const cssClass = SET_CLASS + (opts.cssClass ? " " + opts.cssClass : "");
    return spriteIconResolver(set.url)(name, { ...opts, cssClass });
};

let activeResolver: IconResolver = prefixedSpriteResolver;

/**
 * Adds an icon set: tokens starting with {@code prefix} resolve from the
 * sprite at {@code url} — with the default resolver, or any resolver that
 * asks {@link spriteUrlFor}. A second call for the same prefix replaces the
 * first. {@code prefix} is lowercase-kebab ending in {@code -}, e.g.
 * {@code "brand-"}.
 *
 * @throws Error if the prefix has another shape
 */
export function addIconSprite(prefix: string, url: string): void {
    if (!PREFIX_RE.test(prefix)) {
        throw new Error(`icon set prefix must be lowercase-kebab ending in "-", e.g. "brand-": ${prefix}`);
    }
    iconSprites = iconSprites.filter(set => set.prefix !== prefix).concat({ prefix, url });
    iconSprites.sort((a, b) => b.prefix.length - a.prefix.length || (a.prefix < b.prefix ? -1 : 1));
}

/** Removes the icon set for {@code prefix}; its tokens fall back to the standard sprite. */
export function removeIconSprite(prefix: string): boolean {
    const before = iconSprites.length;
    iconSprites = iconSprites.filter(set => set.prefix !== prefix);
    return iconSprites.length !== before;
}

/** The icon sets, longest prefix first — for diagnostics. */
export function iconSpritesInUse(): { prefix: string; url: string }[] {
    return iconSprites.map(set => ({ ...set }));
}

/**
 * Swaps the active icon resolver process-wide (one page = one resolver), icon
 * sets included: a resolver of your own decides everything. To add to the
 * resolver rather than replace it, wrap {@link getIconResolver}:
 * {@code const prev = getIconResolver(); setIconResolver((n, o) => n === "x" ? mine(o) : prev(n, o));}
 */
export function setIconResolver(resolver: IconResolver): void {
    activeResolver = resolver;
}

/** The active resolver — to wrap it rather than replace it. */
export function getIconResolver(): IconResolver {
    return activeResolver;
}

/**
 * Points the standard sprite at a different URL and goes back to the default
 * resolver; the icon sets stay.
 */
export function setIconSpriteUrl(spriteUrl: string): void {
    fallbackSpriteUrl = spriteUrl;
    activeResolver = prefixedSpriteResolver;
}

// Sprite ids are lowercase-kebab (`folder`, `trash-2`, `circle-check`).
// Anything else — an emoji, a bullet, arbitrary text — is treated as a
// literal glyph and passed through verbatim. This is the migration path:
// legacy `icon: "📁"` data keeps rendering as the emoji, while
// `icon: "folder"` resolves to the sprite. No flag day.
const TOKEN_RE = /^[a-z][a-z0-9-]*$/;

/**
 * Renders an icon to HTML. A token that matches the sprite-id shape goes
 * through the active resolver (SVG by default); any other string (emoji,
 * text) is emitted verbatim as a literal glyph. Returns {@code ""} for a
 * null/blank value so callers can inline it unconditionally:
 * {@code `${renderIcon(node.icon)}<span>…`}.
 */
export function renderIcon(name: string | null | undefined, opts: IconOpts = {}): string {
    if (name == null || name === "") return "";
    if (!TOKEN_RE.test(name)) {
        // Literal glyph (emoji / text). Keep any requested class/id so callers
        // can still style/address the slot consistently.
        const cls = opts.cssClass ? ` class="${esc(opts.cssClass)}"` : "";
        const idAttr = opts.id ? ` id="${esc(opts.id)}"` : "";
        return `<span${idAttr}${cls} aria-hidden="true">${esc(name)}</span>`;
    }
    return activeResolver(name, opts);
}

/** Renderer for the standalone {@link UiIcon} node (type {@code "icon"}). */
export function renderIconNode(node: UiIcon): string {
    return renderIcon(node.name, { cssClass: node.cssClass, title: node.title, id: node.id });
}
