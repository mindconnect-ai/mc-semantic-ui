// Server-side rendering: a UiPage into a finished HTML document.
//
// The renderer that draws it here is the one the browser runs — `render()`
// returns an HTML string and touches no DOM, so it works in plain Node with no
// jsdom. That means there is no second implementation to keep in step: what the
// server sends and what the SPA would have drawn are the same code's output.
//
// This is the Node counterpart of the Java UiPageHtmlMessageConverter, and it
// fills the same four slots in the shell:
//
//   sui-head     <title> and <meta>, which decide how the page is indexed
//   sui-root     the rendered node — the content a crawler reads
//   sui-dialogs  the body-level dialog host
//   sui-model    the tree as JSON, so the bus can MERGE before it re-renders

import { createDefaultRenderer, setIconSpriteUrl } from "@mindconnect-ai/mc-semantic-ui-core";
import type { UiPage } from "@mindconnect-ai/mc-semantic-ui-core/model";

// In Node `import.meta.url` is a file: URL, so the icon renderer's default
// would resolve the sprite to an absolute path on this machine's disk and put
// it in every page it sends. Point it at the URL the browser will use.
setIconSpriteUrl("/sui/icons.svg");

const renderer = createDefaultRenderer();

export interface PageMeta {
    title: string;
    description: string;
    /** Absolute path this page should be indexed under, without query junk. */
    canonical: string;
}

function escapeHtml(value: string): string {
    return value.replace(/[&<>"']/g, c =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));
}

function head(meta: PageMeta): string {
    return `<title>${escapeHtml(meta.title)}</title>\n`
        + `  <meta name="description" content="${escapeHtml(meta.description)}">\n`
        + `  <link rel="canonical" href="${escapeHtml(meta.canonical)}">`;
}

function dialogHost(page: UiPage): string {
    const dialogs = page.dialogs ?? [];
    if (dialogs.length === 0) return "";
    return `<div id="sui-dialogs" class="sui-dialogs">`
        + dialogs.map(d => renderer.render(d)).join("")
        + `</div>`;
}

/**
 * The model the bus seeds itself from. A MERGE patch changes a few fields of a
 * node and leaves the rest alone, which means the client has to know what the
 * rest were — and it never drew this page, so it cannot know unless told.
 *
 * Inside `<script type="application/json">` the only sequence that can end the
 * block early is a literal `</`, so that one is escaped and nothing else has to
 * be.
 */
function modelSeed(page: UiPage): string {
    if (!page.node) return "";
    const json = JSON.stringify(page.node).replace(/<\//g, "<\\/");
    return `<script type="application/json" id="sui-model">${json}</script>`;
}

/**
 * Fills the shell. `template` is index.html — read from disk and passed through
 * Vite in development, the built one in production — so the script and
 * stylesheet tags are whatever that build produced.
 */
export function renderDocument(template: string, page: UiPage, meta: PageMeta): string {
    return template
        .replace("<!--sui-head-->", head(meta))
        .replace("<!--sui-root-->", page.node ? renderer.render(page.node) : "")
        .replace("<!--sui-dialogs-->", dialogHost(page))
        .replace("<!--sui-model-->", modelSeed(page));
}
