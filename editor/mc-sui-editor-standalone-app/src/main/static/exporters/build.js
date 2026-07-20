/*
 * Pure export logic — no browser or DOM dependencies, so it can be unit-tested
 * in plain Node as well as run in the browser. Turns a project's pages into the
 * files a hand-written Semantic UI app is made of: clean page slugs, link
 * rewriting, the static app.js, and per-page UiPage JSON for the server builds.
 */

/** Kebab-case slug from a page name; falls back to "page". */
export function slug(name) {
    return (name || "page").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "page";
}

function uniqueSlug(name, used) {
    const base = slug(name);
    let s = base, i = 2;
    while (used.has(s)) s = `${base}-${i++}`;
    used.add(s);
    return s;
}

// A UiPage wrapper has no renderer handler — the renderable node is its child.
function renderableRoot(root) {
    if (!root) return null;
    return (root.type === "page" && root.node) ? root.node : root;
}

/**
 * Builds the project model the exporters consume.
 * @param {string} projectName
 * @param {Array<{id:string,name:string,root:object|null}>} rawPages
 */
export function buildProject(projectName, rawPages) {
    const used = new Set();
    const pages = rawPages.map(pg => ({
        id: pg.id, name: pg.name, slug: uniqueSlug(pg.name, used), root: renderableRoot(pg.root),
    }));
    // Resolve an authored reference ("page:<id>", "#<id>", a page name, or a
    // bare slug) to a target page's slug.
    const resolveSlug = (ref) => {
        const key = String(ref).replace(/^page:/i, "").replace(/^[#/]+/, "").trim();
        const lower = key.toLowerCase();
        const hit = pages.find(p => p.id === key || p.slug === key || p.name.toLowerCase() === lower);
        return hit ? hit.slug : null;
    };
    return { projectName, entrySlug: pages[0]?.slug ?? null, pages, resolveSlug };
}

/**
 * Rewrites authored cross-page links in a node tree.
 *   • server → a trigger to "page:<id>" becomes the clean path "/<slug>"
 *   • static → the trigger becomes INVOKE navigate {page:"<slug>"} (no backend)
 * Only trigger objects (they carry `behavior`) are touched.
 */
export function rewriteLinks(node, resolveSlug, mode) {
    const clone = JSON.parse(JSON.stringify(node));
    (function walk(n) {
        if (!n || typeof n !== "object") return;
        if (Array.isArray(n)) { n.forEach(walk); return; }
        if (n.behavior && typeof n.url === "string") {
            const s = resolveSlug(n.url);
            if (s) {
                if (mode === "server") {
                    n.url = "/" + s;
                } else {
                    delete n.url; delete n.method;
                    n.behavior = "INVOKE"; n.handler = "navigate"; n.page = s;
                }
            }
        }
        for (const k of Object.keys(n)) walk(n[k]);
    })(clone);
    return clone;
}

const emptyNode = () => ({ type: "text", id: "empty", text: "This page is empty." });

/**
 * The static entry script: pages embedded as UiNode trees, mounted directly,
 * with a tiny backend-free navigation handler for the rewritten links.
 */
export function staticAppJs(project) {
    const pagesObj = {};
    for (const p of project.pages) {
        pagesObj[p.slug] = p.root ? rewriteLinks(p.root, project.resolveSlug, "static") : emptyNode();
    }
    return `import { SuiRenderer, installDefaultHandlers } from "./sui/renderer.js";
import { SuiEventBus } from "./sui/eventbus.js";

// The pages you designed in the visual editor, as UiNode trees.
const PAGES = ${JSON.stringify(pagesObj, null, 2)};
const ENTRY = ${JSON.stringify(project.entrySlug)};

const host = document.getElementById("app");
const renderer = installDefaultHandlers(new SuiRenderer(host));
const bus = new SuiEventBus(renderer, host);

// Backend-free navigation between the bundled pages (links carry the target).
bus.registerClientHandler("navigate", (ctx) => {
  const node = PAGES[ctx.trigger.page];
  if (node) renderer.mount(node);
});

renderer.mount(PAGES[ENTRY]);
`;
}

/** A UiPage JSON body served by the server builds at the page's clean path. */
export function serverPageJson(project, page) {
    const node = page.root ? rewriteLinks(page.root, project.resolveSlug, "server") : emptyNode();
    return JSON.stringify({ type: "page", navigate: "/" + page.slug, node }, null, 2);
}
