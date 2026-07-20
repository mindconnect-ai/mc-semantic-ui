/*
 * Manual save: the editor lives in the browser. Mutations stay local until
 * the user clicks the Save button, at which point we PUT /editor/api/state.
 *
 * Why no auto-save: the editor freely reshuffles the tree, and a half-typed
 * JSON edit can transiently look invalid to the server's strict UiNode
 * deserialiser. Auto-saving every keystroke turns those into noisy 4xx
 * errors. With a save button the user controls when the wire format has to
 * be well-formed.
 */
import type { EditorState } from "./editor-state.js";
import { saveContent } from "./editor-state.js";

/**
 * Lifecycle:
 *  - {@code idle}    — server-saved state matches what the editor has.
 *  - {@code dirty}   — local changes not yet flushed to the server.
 *  - {@code saving}  — PUT in flight.
 *  - {@code error}   — last save failed; the editor stays usable, the user
 *                      can retry by clicking Save again.
 */
export type SaveStatus = "idle" | "dirty" | "saving" | "error";

export class SaveManager {
    private status: SaveStatus = "idle";
    private listener: ((status: SaveStatus) => void) | null = null;

    constructor(private readonly state: EditorState) {
        // Every state mutation flips the editor to "dirty" so the toolbar
        // can show an unsaved-changes indicator. The first snapshot fires
        // synchronously from subscribe() with the empty tree; the boot's
        // replaceRoot lands after that. We skip the very first emission so
        // a freshly-loaded tree doesn't show up as dirty right away.
        let seenBoot = false;
        this.state.subscribe(() => {
            if (!seenBoot) { seenBoot = true; return; }
            this.set("dirty");
        });
    }

    onStatus(listener: (status: SaveStatus) => void): void {
        this.listener = listener;
        // Seed so the toolbar paints the initial state on wire-up.
        listener(this.status);
    }

    /** Triggers an explicit save. Returns true on success, false on failure. */
    async save(): Promise<boolean> {
        this.set("saving");
        try {
            await saveContent({ root: this.state.root ?? undefined });
            this.set("idle");
            return true;
        } catch (err) {
            console.error("SUI editor: save failed", err);
            this.set("error");
            return false;
        }
    }

    private set(status: SaveStatus): void {
        if (this.status === status) return;
        this.status = status;
        this.listener?.(status);
    }
}
