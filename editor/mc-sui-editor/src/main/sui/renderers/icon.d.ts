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
/**
 * The default resolver: one {@code <svg><use></svg>} into a sprite. Size is
 * {@code 1em} (via {@code .sui-icon} CSS) and color is {@code currentColor},
 * so an icon inherits the surrounding text's size and color.
 */
export declare function spriteIconResolver(spriteUrl: string): IconResolver;
/**
 * Swaps the active icon resolver process-wide (one page = one resolver).
 * Pass {@link spriteIconResolver} with a different URL to use another sprite,
 * or a custom function to emit inline SVG / an icon-font element / anything.
 */
export declare function setIconResolver(resolver: IconResolver): void;
/** Convenience: point the default sprite resolver at a different sprite URL. */
export declare function setIconSpriteUrl(spriteUrl: string): void;
/**
 * Renders an icon to HTML. A token that matches the sprite-id shape goes
 * through the active resolver (SVG by default); any other string (emoji,
 * text) is emitted verbatim as a literal glyph. Returns {@code ""} for a
 * null/blank value so callers can inline it unconditionally:
 * {@code `${renderIcon(node.icon)}<span>…`}.
 */
export declare function renderIcon(name: string | null | undefined, opts?: IconOpts): string;
/** Renderer for the standalone {@link UiIcon} node (type {@code "icon"}). */
export declare function renderIconNode(node: UiIcon): string;
