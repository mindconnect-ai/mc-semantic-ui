/*
 * Project/page storage for the visual editor, with two interchangeable
 * implementations chosen at boot:
 *
 *   • LocalStorageProjectStore — everything in the browser (the backend-free
 *     static build).
 *   • RestProjectStore — talks to /editor/api/projects on a server (the
 *     Spring-hosted build). Mirrors the server list in an in-memory cache so
 *     the read accessors stay synchronous for the UiTree builders.
 *
 * Both expose the same shape: synchronous cache reads (projects/project/page)
 * and async mutations + async page-tree load/save. The shell awaits mutations
 * and rebuilds the tree from the (now-updated) cache.
 */

const INDEX_KEY = "sui-editor:index";
const pageKey = (projectId, pageId) => `sui-editor:page:${projectId}:${pageId}`;

function uid(prefix) {
    return `${prefix}-${Math.random().toString(36).slice(2, 8)}${Date.now().toString(36).slice(-3)}`;
}

// ── localStorage ────────────────────────────────────────────────────────────

export class LocalStorageProjectStore {
    get mode() { return "local"; }

    async init() { /* reads localStorage lazily; nothing to load */ }

    _index() {
        try { return JSON.parse(localStorage.getItem(INDEX_KEY)) || { projects: [] }; }
        catch { return { projects: [] }; }
    }
    _save(index) {
        try { localStorage.setItem(INDEX_KEY, JSON.stringify(index)); } catch { /* full/disabled */ }
    }

    projects() { return this._index().projects; }
    project(id) { return this.projects().find(p => p.id === id) || null; }
    page(projectId, pageId) {
        const p = this.project(projectId);
        return p ? (p.pages.find(pg => pg.id === pageId) || null) : null;
    }

    async createProject(name) {
        const index = this._index();
        const proj = { id: uid("proj"), name: name || "Untitled project", createdAt: Date.now(), pages: [] };
        index.projects.push(proj);
        this._save(index);
        return proj;
    }
    async renameProject(id, name) {
        const index = this._index();
        const p = index.projects.find(x => x.id === id);
        if (p) { p.name = name; this._save(index); }
    }
    async deleteProject(id) {
        const index = this._index();
        const p = index.projects.find(x => x.id === id);
        if (!p) return;
        for (const pg of p.pages) localStorage.removeItem(pageKey(id, pg.id));
        index.projects = index.projects.filter(x => x.id !== id);
        this._save(index);
    }

    async createPage(projectId, name) {
        const index = this._index();
        const p = index.projects.find(x => x.id === projectId);
        if (!p) return null;
        const page = { id: uid("page"), name: name || "Untitled page", updatedAt: Date.now() };
        p.pages.push(page);
        this._save(index);
        try { localStorage.setItem(pageKey(projectId, page.id), JSON.stringify({ root: null })); } catch { /* */ }
        return page;
    }
    async renamePage(projectId, pageId, name) {
        const index = this._index();
        const pg = index.projects.find(x => x.id === projectId)?.pages.find(x => x.id === pageId);
        if (pg) { pg.name = name; this._save(index); }
    }
    async deletePage(projectId, pageId) {
        const index = this._index();
        const p = index.projects.find(x => x.id === projectId);
        if (!p) return;
        p.pages = p.pages.filter(x => x.id !== pageId);
        localStorage.removeItem(pageKey(projectId, pageId));
        this._save(index);
    }

    async loadTree(projectId, pageId) {
        try {
            const raw = localStorage.getItem(pageKey(projectId, pageId));
            if (raw) return JSON.parse(raw);
        } catch { /* corrupt */ }
        return { root: null };
    }
    async saveTree(projectId, pageId, root) {
        try { localStorage.setItem(pageKey(projectId, pageId), JSON.stringify({ root: root ?? null })); } catch { /* */ }
        const index = this._index();
        const pg = index.projects.find(x => x.id === projectId)?.pages.find(x => x.id === pageId);
        if (pg) { pg.updatedAt = Date.now(); this._save(index); }
    }

    /** Loads a snapshot (produced by exportAll) — used to seed a fresh install. */
    importAll(snapshot) {
        if (!snapshot || !snapshot.index) return;
        this._save(snapshot.index);
        for (const [key, content] of Object.entries(snapshot.pages || {})) {
            const [projectId, pageId] = key.split("/");
            try { localStorage.setItem(pageKey(projectId, pageId), JSON.stringify(content)); } catch { /* */ }
        }
    }

    async exportAll() {
        const index = this._index();
        const out = { index, pages: {} };
        for (const proj of index.projects) {
            for (const pg of proj.pages) out.pages[`${proj.id}/${pg.id}`] = await this.loadTree(proj.id, pg.id);
        }
        return out;
    }
}

// ── REST ─────────────────────────────────────────────────────────────────────

export class RestProjectStore {
    constructor(base = "/editor/api") {
        this.base = base;
        this._projects = [];
    }
    get mode() { return "server"; }

    async init() { await this._refresh(); }
    async _refresh() { this._projects = await this._json(`${this.base}/projects`); }

    async _json(url, init) {
        const res = await fetch(url, { headers: { Accept: "application/json" }, ...init });
        if (!res.ok) throw new Error(`${init?.method || "GET"} ${url} → ${res.status}`);
        return res.status === 204 ? null : res.json();
    }

    projects() { return this._projects; }
    project(id) { return this._projects.find(p => p.id === id) || null; }
    page(projectId, pageId) {
        const p = this.project(projectId);
        return p ? (p.pages.find(pg => pg.id === pageId) || null) : null;
    }

    async createProject(name) {
        const proj = await this._json(`${this.base}/projects`, {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) });
        await this._refresh();
        return proj;
    }
    async renameProject(id, name) {
        await this._json(`${this.base}/projects/${id}`, {
            method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) });
        await this._refresh();
    }
    async deleteProject(id) {
        await this._json(`${this.base}/projects/${id}`, { method: "DELETE" });
        await this._refresh();
    }

    async createPage(projectId, name) {
        const page = await this._json(`${this.base}/projects/${projectId}/pages`, {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) });
        await this._refresh();
        return page;
    }
    async renamePage(projectId, pageId, name) {
        await this._json(`${this.base}/projects/${projectId}/pages/${pageId}`, {
            method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) });
        await this._refresh();
    }
    async deletePage(projectId, pageId) {
        await this._json(`${this.base}/projects/${projectId}/pages/${pageId}`, { method: "DELETE" });
        await this._refresh();
    }

    async loadTree(projectId, pageId) {
        return this._json(`${this.base}/projects/${projectId}/pages/${pageId}/tree`);
    }
    async saveTree(projectId, pageId, root) {
        await this._json(`${this.base}/projects/${projectId}/pages/${pageId}/tree`, {
            method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ root: root ?? null }) });
        await this._refresh(); // pick up the page's new updatedAt
    }

    importAll() { /* server data is seeded server-side; nothing to import here */ }

    async exportAll() {
        const out = { index: { projects: this._projects }, pages: {} };
        for (const proj of this._projects) {
            for (const pg of proj.pages) out.pages[`${proj.id}/${pg.id}`] = await this.loadTree(proj.id, pg.id);
        }
        return out;
    }
}

// ── Factory ────────────────────────────────────────────────────────────────

/**
 * Picks the store by probing for the server API. If {@code GET
 * /editor/api/projects} answers, we're on a backend (Spring host) → REST;
 * otherwise it's the static build → localStorage.
 */
export async function createProjectStore() {
    try {
        const res = await fetch("/editor/api/projects", { headers: { Accept: "application/json" } });
        if (res.ok) {
            const store = new RestProjectStore();
            store._projects = await res.json();
            return store;
        }
    } catch { /* no server — fall through to localStorage */ }
    return new LocalStorageProjectStore();
}
