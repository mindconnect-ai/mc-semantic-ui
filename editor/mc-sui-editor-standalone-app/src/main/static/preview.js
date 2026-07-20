/*
 * Live preview — "run the project".
 *
 * Mounts a page's UiNode tree with a real SuiRenderer AND a real SuiEventBus,
 * so the page behaves exactly as it would in production: INVOKE/PATCH triggers
 * fire, forms collect and submit, tabs switch, dialogs open. The difference
 * from the editor's own preview pane (which deliberately omits the bus) is that
 * here the bus is fully live.
 *
 * Cross-page navigation stays inside the browser: any trigger that navigates
 * to another page of the same project is intercepted and resolved against
 * localStorage instead of the network. A page is addressable by its id, by
 * "page:<id>", by "#<id>", or by its (case-insensitive) name — so an authored
 * link like UiTrigger.go("page:home") just works. A page bar at the top lets
 * you jump between pages regardless of what the page itself links to.
 */
import { createDefaultRenderer } from "./sui/renderer.js";
import { SuiEventBus } from "./sui/eventbus.js";
import { pinPlainMorpher } from "./offline.js";

/**
 * @param {ProjectStore} store
 * @param {string} projectId
 * @param {string} pageId       page to open first
 * @param {() => void} onExit    called by the "Back" button
 */
export function showPreview(store, projectId, pageId, onExit) {
    const app = document.getElementById("app");
    const proj = store.project(projectId);
    if (!proj) { onExit && onExit(); return; }

    app.innerHTML =
        `<div class="preview-shell">` +
        `  <div class="preview-bar">` +
        `    <button type="button" class="sui-btn preview-exit">← Back</button>` +
        `    <strong class="preview-project"></strong>` +
        `    <span class="preview-pages"></span>` +
        `  </div>` +
        `  <div class="preview-canvas"><div id="preview-root"></div></div>` +
        `</div>`;

    app.querySelector(".preview-exit").addEventListener("click", () => onExit && onExit());
    app.querySelector(".preview-project").textContent = proj.name;

    const root = document.getElementById("preview-root");
    const renderer = pinPlainMorpher(createDefaultRenderer().attach(root));
    const bus = new SuiEventBus(renderer, root);
    // Backend-free: no URLs to route, instant handlers, and we own history.
    bus.setHistoryEnabled(false);
    bus.setLoadingPolicy("manual");
    bus.setOnNavigate((href) => {
        const target = resolvePage(proj, href);
        if (target) mount(target.id);
        // Unknown targets (external URLs, unmatched routes) are ignored in
        // preview — there is no server to fetch them from.
    });

    // Page switcher — one button per page in the project.
    const pagesBar = app.querySelector(".preview-pages");
    for (const pg of proj.pages) {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "sui-btn preview-page-btn";
        b.textContent = pg.name;
        b.dataset.pageId = pg.id;
        b.addEventListener("click", () => mount(pg.id));
        pagesBar.appendChild(b);
    }

    async function mount(id) {
        pagesBar.querySelectorAll(".preview-page-btn").forEach(b =>
            b.classList.toggle("active", b.dataset.pageId === id));
        const { root: tree } = await store.loadTree(projectId, id);
        // A UiPage wrapper has no renderer handler — mount its node child.
        const renderable = (tree && tree.type === "page" && tree.node) ? tree.node : tree;
        if (renderable && renderable.type) {
            renderer.mount(renderable);
        } else {
            root.innerHTML = `<p class="preview-empty">This page is empty. ` +
                `Open it in the editor to add content.</p>`;
        }
    }

    mount(pageId);
}

/**
 * Resolves a navigation href to a page of {@code proj}, or null. Accepts the
 * bare page id, "page:<id>", "#<id>", "/<id>", or the page's name.
 */
function resolvePage(proj, href) {
    if (!href) return null;
    const raw = String(href).trim();
    const key = raw.replace(/^page:/i, "").replace(/^[#/]+/, "").trim();
    const lower = key.toLowerCase();
    return proj.pages.find(pg =>
        pg.id === key || pg.id === raw || pg.name.toLowerCase() === lower) || null;
}
