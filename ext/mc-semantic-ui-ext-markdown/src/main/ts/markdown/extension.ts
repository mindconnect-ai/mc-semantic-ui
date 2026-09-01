import type { SuiRenderer } from "@mindconnect-ai/mc-semantic-ui-core";

// escapeHtml is inlined (not imported from the core bundle) so the compiled
// extension.js has NO runtime import at all. That keeps the bundle portable:
// it loads from a CDN, from under a path prefix, or straight off a Spring app
// with nothing to resolve and no import map to declare. The `import type`
// above is erased at compile time. Same trick as the chart and diagram
// extensions.
const HTML_ESCAPE: Record<string, string> =
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
function escapeHtml(value: unknown): string {
    if (value == null) return "";
    return String(value).replace(/[&<>"']/g, ch => HTML_ESCAPE[ch]!);
}

/**
 * Wire shape of the {@code markdown} node, mirroring the Java
 * {@code UiMarkdown} class.
 */
interface UiMarkdownNode {
    type: "markdown";
    id: string;
    title?: string;
    cssClass?: string;
    content?: string;
}

/**
 * ESM build of marked@12 from jsdelivr. Resolved lazily on install so a host
 * that never includes the markdown extension pays nothing for it.
 */
const MARKED_ESM_URL = "https://cdn.jsdelivr.net/npm/marked@12/lib/marked.esm.js";

interface MarkedModule {
    marked: { parse(input: string): string };
}

let loadPromise: Promise<MarkedModule> | null = null;

function loadMarked(): Promise<MarkedModule> {
    if (!loadPromise) {
        loadPromise = import(/* @vite-ignore */ MARKED_ESM_URL) as Promise<MarkedModule>;
    }
    return loadPromise;
}

/**
 * Registers the {@code markdown} handler on the supplied renderer. Returns
 * a promise that resolves once {@code marked} is loaded; the renderer is
 * usable immediately because the handler is registered synchronously and the
 * library load is awaited inside the handler when needed.
 *
 * <p>The Markdown source is treated as trusted: the security boundary lies
 * with whoever produced the {@code content} string (typically server-side
 * authored copy or LLM output that has been reviewed upstream). Anchor tags
 * are rewritten to open in a new tab so links don't replace the SPA shell.
 */
export async function install(renderer: SuiRenderer): Promise<void> {
    let marked: MarkedModule["marked"] | null = null;

    renderer.register<UiMarkdownNode>("markdown", (node) => {
        const className = node.cssClass
            ? `sui-markdown ${escapeHtml(node.cssClass)}`
            : "sui-markdown";
        const id = escapeHtml(node.id);

        // If the library is still resolving on the first render, we degrade
        // gracefully to an escaped <pre>. The renderer will re-paint on the
        // next patch / re-render once marked is loaded.
        if (!marked) {
            return `<div class="${className}" id="${id}"><pre>${escapeHtml(node.content ?? "")}</pre></div>`;
        }
        const raw = marked.parse(node.content ?? "");
        const html = raw.replace(/<a\s/g, '<a target="_blank" rel="noopener noreferrer" ');
        return `<div class="${className}" id="${id}">${html}</div>`;
    });

    const mod = await loadMarked();
    marked = mod.marked;
}
