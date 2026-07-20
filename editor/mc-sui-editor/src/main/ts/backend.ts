/*
 * Pluggable persistence for the editor.
 *
 * The editor was born Spring-hosted: it talked to four REST endpoints under
 * {@code /editor/api} (schema, default-instance, load, save). To let the very
 * same editor code run as a pure in-browser app with no backend, those four
 * calls are hidden behind {@link EditorBackend}. The Spring host installs a
 * {@link RestBackend}; the standalone app installs a
 * {@code LocalStorageBackend} (see the standalone module) that serves a
 * bundled schema and stores trees in {@code localStorage}.
 *
 * A single module-level singleton holds the active backend. The editor mounts
 * one instance at a time, so a singleton is enough and spares every component
 * from threading a backend reference through its constructor. {@link bootEditor}
 * sets it before anything loads; if nothing sets it, {@link getBackend} lazily
 * defaults to the REST backend so the Spring-hosted editor keeps working with
 * no wiring.
 */
import type { EditorContent, Schema, UiNodeJson } from "./types.js";

/**
 * The four operations the editor needs from its host. Everything else the
 * editor does is client-only (tree mutation, property editing, preview).
 */
export interface EditorBackend {
    /** The node catalogue that drives the add-picker and property panel. */
    loadSchema(): Promise<Schema>;
    /** The tree currently being edited. */
    loadContent(): Promise<EditorContent>;
    /** Persist the whole tree. */
    saveContent(content: EditorContent): Promise<void>;
    /** A fresh default-populated instance of {@code type} (unique id). */
    loadDefault(type: string): Promise<UiNodeJson>;
}

/** Fixed API root — see EditorRestController for why it never moves. */
const API_BASE = "/editor/api";

/**
 * The original Spring-backed behaviour: talk to {@code /editor/api/*}. Kept
 * byte-for-byte compatible with the pre-refactor free functions so the
 * Spring-hosted editor is unaffected.
 */
export class RestBackend implements EditorBackend {
    constructor(private readonly base: string = API_BASE) {}

    async loadSchema(): Promise<Schema> {
        const res = await fetch(`${this.base}/schema`, { headers: { Accept: "application/json" } });
        if (!res.ok) throw new Error(`schema fetch failed: ${res.status}`);
        const list = await res.json() as Array<Schema[string]>;
        const map: Schema = {};
        for (const meta of list) map[meta.type] = meta;
        return map;
    }

    async loadContent(): Promise<EditorContent> {
        const res = await fetch(`${this.base}/state`, { headers: { Accept: "application/json" } });
        if (!res.ok) throw new Error(`state fetch failed: ${res.status}`);
        return await res.json() as EditorContent;
    }

    async saveContent(content: EditorContent): Promise<void> {
        const res = await fetch(`${this.base}/state`, {
            method: "PUT",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify(content),
        });
        if (!res.ok) {
            const text = await res.text().catch(() => "");
            throw new Error(`state save failed: ${res.status} ${text}`);
        }
    }

    async loadDefault(type: string): Promise<UiNodeJson> {
        const res = await fetch(`${this.base}/default/${encodeURIComponent(type)}`, {
            headers: { Accept: "application/json" },
        });
        if (!res.ok) throw new Error(`default fetch for ${type} failed: ${res.status}`);
        return await res.json() as UiNodeJson;
    }
}

let active: EditorBackend | null = null;

/** Installs the backend the editor will use. Call before {@link bootEditor}. */
export function setBackend(backend: EditorBackend): void {
    active = backend;
}

/** The active backend, defaulting to {@link RestBackend} for Spring hosts. */
export function getBackend(): EditorBackend {
    if (!active) active = new RestBackend();
    return active;
}
