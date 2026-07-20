import type { SuiRenderer } from "/sui/renderer.js";
import { escapeHtml } from "/sui/renderer.js";

/**
 * Wire shape of the {@code json-viewer} node, mirroring the Java
 * {@code UiJsonViewer} class. The renderer doesn't import a separate
 * model type because extensions are free to declare their own shape inline.
 */
interface UiJsonViewerNode {
    type: "json-viewer";
    id: string;
    json?: string;
    expandLevel?: number;
    theme?: string;
    cssClass?: string;
}

/**
 * URL of the andypf-json-viewer ESM build on jsdelivr. Resolved at install
 * time via dynamic import so the dependency is lazy: hosts that never
 * include the json-viewer extension never pay its download cost.
 */
const ANDYPF_ESM_URL = "https://cdn.jsdelivr.net/npm/@andypf/json-viewer@2/dist/iife/index.js";

let loadPromise: Promise<unknown> | null = null;

/**
 * Loads the andypf-json-viewer web component once per page. We cache the
 * promise so concurrent installs of the same extension don't trigger
 * duplicate downloads — and so a failed first load doesn't get retried
 * indefinitely on every render.
 */
function loadAndypf(): Promise<unknown> {
    if (!loadPromise) {
        loadPromise = import(/* @vite-ignore */ ANDYPF_ESM_URL);
    }
    return loadPromise;
}

/**
 * Registers the {@code json-viewer} handler on the supplied renderer and
 * kicks off the lazy load of the underlying web component. The returned
 * promise resolves once the component is registered with
 * {@code customElements} — but the renderer is usable immediately, as the
 * web component renders blank until ready and self-upgrades when its
 * definition lands.
 */
export async function install(renderer: SuiRenderer): Promise<void> {
    renderer.register<UiJsonViewerNode>("json-viewer", renderJsonViewer);
    await loadAndypf();
}

function renderJsonViewer(node: UiJsonViewerNode): string {
    // The JSON payload is HTML-attribute-escaped: the web component reads
    // it raw from the `data` attribute and parses it itself.
    const data = escapeHtml(node.json ?? "");
    const id = escapeHtml(node.id);
    const expandLevel = node.expandLevel != null
        ? `expand-level="${node.expandLevel}"`
        : "";
    const theme = node.theme ? `theme="${escapeHtml(node.theme)}"` : "";
    const cssClass = node.cssClass ? `class="${escapeHtml(node.cssClass)}"` : "";

    return `<andypf-json-viewer id="${id}" ${cssClass} data="${data}" ${expandLevel} ${theme}
              show-data-types="false" show-toolbar="false"></andypf-json-viewer>`;
}
