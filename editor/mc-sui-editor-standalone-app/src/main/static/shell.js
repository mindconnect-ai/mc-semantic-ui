/*
 * Backend-free Semantic UI visual editor — the shell.
 *
 * Views, swapped inside #app:
 *   • Projects list — the landing page: just the projects (core UiTree, flat),
 *     create / rename / delete / open.
 *   • Project page  — one project's pages (core UiTree): new page, open in the
 *     editor, preview, rename, delete, and Download this project as an app.
 *   • Editor        — the mc-sui-editor bootEditor(), wired to a localStorage
 *     backend for the selected page.
 *   • Preview       — the page mounted with a real renderer + event bus.
 *
 * The node catalogue (schema) and default-instance factory output are loaded
 * once from data/schema.json + data/defaults.json — the build dumps them from
 * the same NodeRegistry the Spring editor uses.
 */
import { createDefaultRenderer, renderIcon } from "./sui/renderer.js";
import { SuiEventBus } from "./sui/eventbus.js";
import "./sui-editor/sui-editor-element.js";   // registers the <sui-editor> element
import { createProjectStore } from "./store.js";
import { showPreview } from "./preview.js";
import { openExportDialog } from "./exporters/index.js";
import { pinPlainMorpher } from "./offline.js";

// The store is chosen at boot: REST when a backend is present, else localStorage.
let store;
let schema = [];
let defaults = {};
let catalogue = null;   // { schema, defaults } for <sui-editor>, built lazily

const app = () => document.getElementById("app");

// ── Boot ─────────────────────────────────────────────────────────────────────

async function boot() {
    // Pick the store: REST (server present) or localStorage (static build).
    store = await createProjectStore();

    // The node catalogue drives the local editor backend and is only needed in
    // localStorage mode — in server mode the editor fetches it from the REST
    // endpoints instead, so a missing ./data/ (static-only asset) is fine.
    if (store.mode === "local") {
        try {
            [schema, defaults] = await Promise.all([
                fetch("./data/schema.json").then(r => r.json()),
                fetch("./data/defaults.json").then(r => r.json()),
            ]);
        } catch (err) {
            app().innerHTML = `<p class="fatal">Failed to load the node catalogue (data/schema.json). ` +
                `Serve this app from a static server so fetch() can read it.</p>`;
            console.error("shell: catalogue load failed", err);
            return;
        }

        // First run (empty localStorage): seed the bundled starter project so
        // there's something to open. Deleting the sample sticks (no re-seed).
        if (!store.projects().length) {
            try {
                const res = await fetch("./seed/seed.json");
                if (res.ok) store.importAll(await res.json());
            } catch (_) { /* no seed shipped — start empty */ }
        }
    }

    showProjects();
}

// A view host + a mounted core renderer/bus. Every view is built from UiNodes
// and driven by INVOKE handlers — no fetch, no framework.
function mountView(buildPage, registerHandlers) {
    app().innerHTML = `<div id="home-root" class="home-root"></div>`;
    const root = document.getElementById("home-root");
    const renderer = pinPlainMorpher(createDefaultRenderer().attach(root));
    const bus = new SuiEventBus(renderer, root);
    bus.setHistoryEnabled(false);
    bus.setLoadingPolicy("manual");
    registerHandlers(bus);
    renderer.mount(buildPage());
    return { renderer, bus };
}

// ── Projects list (landing) ────────────────────────────────────────────────────

function showProjects() {
    mountView(projectsPage, registerProjectsHandlers);
}

function projectsPage() {
    return {
        type: "stack", id: "home", gap: 16,
        children: [
            {
                type: "header", id: "home-header", brand: "SUI Visual Editor",
                extras: [
                    { type: "action", id: "new-project", label: "New project", icon: "add", style: "PRIMARY",
                      onClick: { behavior: "INVOKE", handler: "project.new" } },
                ],
            },
            projectsListOrEmpty(),
        ],
    };
}

function projectsListOrEmpty() {
    const projects = store.projects();
    if (!projects.length) {
        return emptyState("home-empty", "No projects yet.",
            "Click “New project” to create your first project.");
    }
    return { type: "tree", id: "projects-tree", title: "Projects", nodes: projects.map(projectRow) };
}

function projectRow(proj) {
    return {
        type: "tree-node",
        id: `p-${proj.id}`,
        labelNode: rowLabel(`prow-${proj.id}`, [
            { type: "action", id: `po-${proj.id}`, label: proj.name, icon: "folder",
              appearance: "LINK", style: "SECONDARY",
              onClick: { behavior: "INVOKE", handler: "project.open", projectId: proj.id } },
            { type: "text", id: `pc-${proj.id}`, text: `${proj.pages.length} page(s)`, cssClass: "tree-muted" },
            iconBtn(`pr-${proj.id}`, "edit", "Rename", "project.rename", { projectId: proj.id }),
            iconBtn(`pd-${proj.id}`, "delete", "Delete", "project.delete", { projectId: proj.id },
                `Delete project “${proj.name}” and all its pages?`),
        ]),
    };
}

function registerProjectsHandlers(bus) {
    bus.registerClientHandler("project.new", async () => {
        const name = prompt("Project name:", "My project");
        if (name == null) return;
        await store.createProject(name.trim() || "Untitled project");
        return replace("home", projectsPage());
    });
    bus.registerClientHandler("project.rename", async (ctx) => {
        const proj = store.project(ctx.trigger.projectId);
        if (!proj) return;
        const name = prompt("Rename project:", proj.name);
        if (name == null) return;
        await store.renameProject(proj.id, name.trim() || proj.name);
        return replace("home", projectsPage());
    });
    bus.registerClientHandler("project.delete", async (ctx) => {
        await store.deleteProject(ctx.trigger.projectId);
        return replace("home", projectsPage());
    });
    bus.registerClientHandler("project.open", (ctx) => {
        showProject(ctx.trigger.projectId);
    });
}

// ── Project page (one project's pages) ─────────────────────────────────────────

function showProject(projectId) {
    if (!store.project(projectId)) { showProjects(); return; }
    mountView(() => projectPage(projectId), (bus) => registerProjectHandlers(bus, projectId));
}

function projectPage(projectId) {
    const proj = store.project(projectId);
    return {
        type: "stack", id: "proj", gap: 16,
        children: [
            {
                type: "header", id: "proj-header", brand: proj.name,
                extras: [
                    { type: "action", id: "back", label: "Projects", icon: "back", style: "SECONDARY",
                      onClick: { behavior: "INVOKE", handler: "nav.projects" } },
                    { type: "action", id: "new-page", label: "New page", icon: "add", style: "PRIMARY",
                      onClick: { behavior: "INVOKE", handler: "page.new", projectId } },
                    { type: "action", id: "preview-project", label: "Preview", icon: "show", style: "SECONDARY",
                      onClick: { behavior: "INVOKE", handler: "project.preview", projectId } },
                    { type: "action", id: "download", label: "Download app", icon: "download", style: "SECONDARY",
                      onClick: { behavior: "INVOKE", handler: "project.export", projectId } },
                ],
            },
            pagesListOrEmpty(projectId),
        ],
    };
}

function pagesListOrEmpty(projectId) {
    const proj = store.project(projectId);
    if (!proj.pages.length) {
        return emptyState("proj-empty", "No pages yet.",
            "Click “New page” to add a page, then open it in the editor.");
    }
    return { type: "tree", id: "pages-tree", title: "Pages", nodes: proj.pages.map(pg => pageRow(projectId, pg)) };
}

function pageRow(projectId, pg) {
    return {
        type: "tree-node",
        id: `pg-${pg.id}`,
        labelNode: rowLabel(`pgrow-${pg.id}`, [
            { type: "action", id: `po-${pg.id}`, label: pg.name, icon: "document",
              appearance: "LINK", style: "SECONDARY",
              onClick: { behavior: "INVOKE", handler: "page.open", projectId, pageId: pg.id } },
            iconBtn(`pgv-${pg.id}`, "show",   "Preview", "page.preview", { projectId, pageId: pg.id }),
            iconBtn(`pgr-${pg.id}`, "edit",   "Rename",  "page.rename",  { projectId, pageId: pg.id }),
            iconBtn(`pgd-${pg.id}`, "delete", "Delete",  "page.delete",  { projectId, pageId: pg.id },
                `Delete page “${pg.name}”?`),
        ]),
    };
}

function registerProjectHandlers(bus, projectId) {
    bus.registerClientHandler("nav.projects", () => { showProjects(); });

    bus.registerClientHandler("page.new", async () => {
        const name = prompt("Page name:", "Home");
        if (name == null) return;
        await store.createPage(projectId, name.trim() || "Untitled page");
        return replace("proj", projectPage(projectId));
    });
    bus.registerClientHandler("page.rename", async (ctx) => {
        const pg = store.page(projectId, ctx.trigger.pageId);
        if (!pg) return;
        const name = prompt("Rename page:", pg.name);
        if (name == null) return;
        await store.renamePage(projectId, ctx.trigger.pageId, name.trim() || pg.name);
        return replace("proj", projectPage(projectId));
    });
    bus.registerClientHandler("page.delete", async (ctx) => {
        await store.deletePage(projectId, ctx.trigger.pageId);
        return replace("proj", projectPage(projectId));
    });
    bus.registerClientHandler("page.open", (ctx) => {
        showEditor(projectId, ctx.trigger.pageId);
    });
    bus.registerClientHandler("page.preview", (ctx) => {
        showPreview(store, projectId, ctx.trigger.pageId, () => showProject(projectId));
    });
    bus.registerClientHandler("project.preview", () => {
        const proj = store.project(projectId);
        const first = proj && proj.pages[0];
        if (!first) { alert("Add a page to this project first."); return; }
        showPreview(store, projectId, first.id, () => showProject(projectId));
    });
    bus.registerClientHandler("project.export", () => {
        openExportDialog(store, projectId);
    });
}

// ── Editor view ────────────────────────────────────────────────────────────────

// The node catalogue for <sui-editor>. Local build: the bundled schema/defaults.
// Server build: fetched from the library's REST endpoints (one default per type).
async function getCatalogue() {
    if (catalogue) return catalogue;
    if (store.mode === "server") {
        const list = await fetch("/editor/api/schema", { headers: { Accept: "application/json" } }).then(r => r.json());
        const defs = {};
        await Promise.all(list.map(async (m) => {
            defs[m.type] = await fetch(`/editor/api/default/${encodeURIComponent(m.type)}`,
                { headers: { Accept: "application/json" } }).then(r => r.json());
        }));
        catalogue = { schema: list, defaults: defs };
    } else {
        catalogue = { schema, defaults };
    }
    return catalogue;
}

async function showEditor(projectId, pageId) {
    const proj = store.project(projectId);
    const pg = store.page(projectId, pageId);
    if (!proj || !pg) { showProject(projectId); return; }

    // The app owns the chrome (back / preview / save status); <sui-editor> owns
    // the panes. We load the page tree, hand it in as `value`, and persist on
    // every `change` — the component never touches storage.
    app().innerHTML =
        `<div class="editor-view">` +
        `  <div class="editor-toolbar sui-editor-toolbar">` +
        `    <button type="button" class="sui-btn" data-act="back">${renderIcon("back")} Projects</button>` +
        `    <h1>${escapeHtml(proj.name)} · ${escapeHtml(pg.name)}</h1>` +
        `    <span class="editor-status"></span>` +
        `    <div class="sui-editor-actions">` +
        `      <button type="button" class="sui-btn" data-act="preview">${renderIcon("show")} Preview</button>` +
        `    </div>` +
        `  </div>` +
        `  <sui-editor class="editor-host"></sui-editor>` +
        `</div>`;

    const ed = app().querySelector("sui-editor");
    const status = app().querySelector(".editor-status");
    app().querySelector('[data-act="back"]').addEventListener("click", () => showProject(projectId));
    app().querySelector('[data-act="preview"]').addEventListener("click",
        () => showPreview(store, projectId, pageId, () => showEditor(projectId, pageId)));

    ed.catalogue = await getCatalogue();
    const { root } = await store.loadTree(projectId, pageId);
    ed.value = { root };
    ed.addEventListener("change", async (e) => {
        status.textContent = "Saving…";
        try {
            await store.saveTree(projectId, pageId, e.detail.root);
            status.textContent = "Saved";
        } catch (err) {
            status.textContent = "Save failed";
            console.error("save failed", err);
        }
    });
}

// ── Shared UiNode helpers ───────────────────────────────────────────────────────

function emptyState(id, title, note) {
    return {
        type: "stack", id, gap: 8, cssClass: "home-empty",
        children: [
            { type: "text", id: `${id}-t`, text: title, cssClass: "home-empty-title" },
            { type: "text", id: `${id}-n`, text: note, cssClass: "home-empty-note" },
        ],
    };
}

// A tree node's labelNode: the name plus its inline action buttons.
function rowLabel(id, children) {
    return { type: "stack", id, direction: "HORIZONTAL", gap: 8, cssClass: "tree-row-label", children };
}

function btn(id, label, handler, extra, confirm) {
    const onClick = { behavior: "INVOKE", handler, ...extra };
    const action = { type: "action", id, label, style: "SECONDARY", appearance: "BUTTON", onClick };
    if (confirm) action.confirm = confirm;
    return action;
}

/**
 * Icon-only button. The icon comes from the shared icon library (the sprite at
 * /sui/icons.svg) rather than an emoji, so the chrome matches the widgets and
 * follows the theme; `label` stays as the accessible name / tooltip.
 */
function iconBtn(id, icon, label, handler, extra, confirm) {
    const action = btn(id, label, handler, extra, confirm);
    action.icon = icon;
    action.appearance = "ICON";
    return action;
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, c =>
        c === "&" ? "&amp;" : c === "<" ? "&lt;" : c === ">" ? "&gt;" : "&quot;");
}

// A REPLACE patch that repaints a whole view stack in place, keeping the
// mounted renderer + bus.
function replace(targetId, node) {
    return { patches: [{ op: "REPLACE", targetId, node }] };
}

boot();
