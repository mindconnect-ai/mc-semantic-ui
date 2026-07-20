/*
 * "Download app" — export ONE project as a plain, hand-written Semantic UI app.
 *
 * What you download is the standard setup from the docs (index.html + app.js +
 * the sui/ bundle), NOT a custom runtime. Three targets:
 *
 *   • Static site        — app.js embeds your pages and mounts them; navigation
 *                          between pages is backend-free.
 *   • Spring Boot app      — one clean path per page returns UiPage JSON; a
 *                          filter forwards browser navigations to the shell.
 *   • Node.js / Express app — same, with an Express server.
 *
 * Authored cross-page links (a trigger to "page:<id>") are rewritten to the
 * real target: a clean path for the server builds, an in-browser navigation for
 * the static build. The exported project is a real app you can keep building on.
 */
import { fetchAppFiles } from "./app-files.js";
import { zipSync, downloadBlob } from "./zip.js";
import { buildProject, staticAppJs, serverPageJson, slug } from "./build.js";
import {
    indexHtml, serverAppJs, STATIC_README,
    SPRING_POM, SPRING_APP, SPRING_PAGES, SPRING_FILTER, SPRING_PROPS, SPRING_README,
    NODE_SERVER, NODE_PKG, NODE_README,
} from "./templates.js";

const SPRING_PKG_DIR = "src/main/java/ai/mindconnect/ui/app";

/** Opens the export chooser for a single project. */
export function openExportDialog(store, projectId) {
    const proj = store.project(projectId);
    if (!proj) return;

    const dialog = document.createElement("dialog");
    dialog.className = "export-dialog";
    dialog.innerHTML =
        `<form method="dialog" class="export-body">` +
        `  <h3>Download “${escapeHtml(proj.name)}”</h3>` +
        `  <p class="export-note">A plain Semantic UI app (index.html + app.js) — the pages, not the editor.</p>` +
        `  <ul class="export-list">` +
        `    <li><button type="button" data-target="static"><strong>Static site</strong>` +
        `        <small>index.html + app.js. No backend; open on any static host.</small></button></li>` +
        `    <li><button type="button" data-target="spring"><strong>Spring Boot app</strong>` +
        `        <small>One clean path per page (UiPage JSON) + SPA filter. mvn spring-boot:run</small></button></li>` +
        `    <li><button type="button" data-target="node"><strong>Node.js / Express app</strong>` +
        `        <small>One clean path per page (UiPage JSON) + SPA forward. npm start</small></button></li>` +
        `  </ul>` +
        `  <div class="export-status" hidden></div>` +
        `  <menu><button type="button" data-action="cancel" class="sui-btn">Close</button></menu>` +
        `</form>`;
    document.body.appendChild(dialog);

    const status = dialog.querySelector(".export-status");
    const setStatus = (msg) => { status.hidden = false; status.textContent = msg; };

    dialog.addEventListener("click", async (e) => {
        if (e.target.closest('[data-action="cancel"]')) { dialog.close(); dialog.remove(); return; }
        const target = e.target.closest("button")?.dataset.target;
        if (!target) return;
        try {
            setStatus("Packaging…");
            await EXPORTERS[target](store, projectId);
            setStatus("Downloaded. Check your browser's downloads.");
        } catch (err) {
            console.error("export failed", err);
            setStatus("Export failed — see console. (Serve the app from a static server so it can read its own files.)");
        }
    });

    dialog.showModal();
}

// ── Project model ──────────────────────────────────────────────────────────────

// Reads a project out of the store into the pure build model (see build.js).
// Async because page trees may come from a server (RestProjectStore).
async function project(store, projectId) {
    const proj = store.project(projectId);
    const raw = [];
    for (const pg of proj.pages) {
        const { root } = await store.loadTree(projectId, pg.id);
        raw.push({ id: pg.id, name: pg.name, root });
    }
    return buildProject(proj.name, raw);
}

// ── Assets ─────────────────────────────────────────────────────────────────────

// The compiled core bundle (renderer + event bus + themes). No editor, no
// player, no .d.ts — just what a hand-written app imports.
async function coreAssets() {
    const all = await fetchAppFiles();
    return all.filter(f => f.name.startsWith("sui/") && !f.name.endsWith(".d.ts"));
}

const prefix = (files, dir) => files.map(f => ({ name: `${dir}/${f.name}`, data: f.data }));

// ── Targets ────────────────────────────────────────────────────────────────────

async function exportStatic(store, projectId) {
    const proj = await project(store, projectId);
    const root = slug(proj.projectName) + "-static";
    const files = prefix(await coreAssets(), root);
    files.push({ name: `${root}/index.html`, data: indexHtml(proj.projectName) });
    files.push({ name: `${root}/app.js`, data: staticAppJs(proj) });
    files.push({ name: `${root}/README.md`, data: STATIC_README });
    downloadBlob(zipSync(files), `${root}.zip`);
}

async function exportSpring(store, projectId) {
    const proj = await project(store, projectId);
    const root = slug(proj.projectName) + "-springboot";
    const staticDir = `${root}/src/main/resources/static`;
    const pagesDir = `${root}/src/main/resources/pages`;
    const files = prefix(await coreAssets(), staticDir);
    files.push({ name: `${staticDir}/index.html`, data: indexHtml(proj.projectName) });
    files.push({ name: `${staticDir}/app.js`, data: serverAppJs("/" + proj.entrySlug) });
    for (const p of proj.pages) files.push({ name: `${pagesDir}/${p.slug}.json`, data: serverPageJson(proj, p) });
    files.push({ name: `${root}/pom.xml`, data: SPRING_POM });
    files.push({ name: `${root}/${SPRING_PKG_DIR}/Application.java`, data: SPRING_APP });
    files.push({ name: `${root}/${SPRING_PKG_DIR}/PagesController.java`, data: SPRING_PAGES });
    files.push({ name: `${root}/${SPRING_PKG_DIR}/SpaForwardingFilter.java`, data: SPRING_FILTER });
    files.push({ name: `${root}/src/main/resources/application.properties`, data: SPRING_PROPS });
    files.push({ name: `${root}/README.md`, data: SPRING_README });
    downloadBlob(zipSync(files), `${root}.zip`);
}

async function exportNode(store, projectId) {
    const proj = await project(store, projectId);
    const root = slug(proj.projectName) + "-node";
    const files = prefix(await coreAssets(), `${root}/public`);
    files.push({ name: `${root}/public/index.html`, data: indexHtml(proj.projectName) });
    files.push({ name: `${root}/public/app.js`, data: serverAppJs("/" + proj.entrySlug) });
    for (const p of proj.pages) files.push({ name: `${root}/pages/${p.slug}.json`, data: serverPageJson(proj, p) });
    files.push({ name: `${root}/server.js`, data: NODE_SERVER });
    files.push({ name: `${root}/package.json`, data: NODE_PKG });
    files.push({ name: `${root}/README.md`, data: NODE_README });
    downloadBlob(zipSync(files), `${root}.zip`);
}

const EXPORTERS = { static: exportStatic, spring: exportSpring, node: exportNode };

// ── utils ────────────────────────────────────────────────────────────────────

function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, c =>
        c === "&" ? "&amp;" : c === "<" ? "&lt;" : c === ">" ? "&gt;" : "&quot;");
}
