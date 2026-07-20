/*
 * Single in-browser source of truth for the editor.
 *
 * Holds the current root UiNode tree, the schema (loaded once), and the
 * selection path. Mutations go through replaceRoot() / setSelection() so
 * every interested view re-renders from one consistent state — no half-
 * updated DOM where the tree shows the new node but the panel still
 * displays the old one.
 *
 * Subscribers register a listener; the state fires after every mutation
 * with a snapshot. Persistence (debounced PUT to /editor/api/state) is a
 * separate concern wired up in iteration 8 — for now the state is
 * client-only between page reloads.
 */
import type { EditorContent, Schema, UiNodeJson } from "./types.js";

/** Selection path as a sequence of property/index hops from the root. */
export type SelectionPath = ReadonlyArray<string | number>;

export interface EditorSnapshot {
    readonly root: UiNodeJson | null;
    readonly selection: SelectionPath;
    readonly schema: Schema;
}

export type EditorListener = (snapshot: EditorSnapshot) => void;

export class EditorState {
    private rootNode: UiNodeJson | null = null;
    private selection: SelectionPath = [];
    private readonly listeners = new Set<EditorListener>();

    constructor(private readonly schema: Schema) {}

    // ── Reads ────────────────────────────────────────────────────────────

    get root(): UiNodeJson | null { return this.rootNode; }
    get selectionPath(): SelectionPath { return this.selection; }
    get schemaMap(): Schema { return this.schema; }

    snapshot(): EditorSnapshot {
        return {
            root: this.rootNode,
            selection: this.selection,
            schema: this.schema,
        };
    }

    /**
     * Walks the tree along the current selection and returns the node it
     * points at — {@code null} when the selection is empty or the path is
     * stale (e.g. a parent was just deleted). Read-only: views must not
     * mutate the returned reference.
     */
    selectedNode(): UiNodeJson | null {
        return this.nodeAt(this.selection);
    }

    nodeAt(path: SelectionPath): UiNodeJson | null {
        if (!this.rootNode) return null;
        let cursor: any = this.rootNode;
        for (const hop of path) {
            if (cursor == null) return null;
            cursor = cursor[hop as any];
        }
        return (cursor && typeof cursor === "object" && "type" in cursor)
            ? cursor as UiNodeJson
            : null;
    }

    /**
     * Like nodeAt() but returns *anything* at the path — including arrays and
     * entry-wrapper objects without a type discriminator. Used by mutators
     * that need to address the parent of a list entry (where parent itself is
     * an array, not a UiNode).
     */
    private rawAt(path: SelectionPath): any {
        if (!this.rootNode) return null;
        let cursor: any = this.rootNode;
        for (const hop of path) {
            if (cursor == null) return null;
            cursor = cursor[hop as any];
        }
        return cursor;
    }

    // ── Mutations ────────────────────────────────────────────────────────

    /**
     * Replaces the whole tree. Used after the initial GET /editor/api/state
     * and as a recovery hatch when the JSON editor rewrites the root.
     * Pass {@code keepSelection=true} to retain the previous selection path
     * (e.g. for in-place JSON edits of the selected node).
     */
    replaceRoot(root: UiNodeJson | null, keepSelection = false): void {
        this.rootNode = root;
        if (!keepSelection) {
            // A previously-valid selection might now point into nothing.
            this.selection = [];
        }
        this.emit();
    }

    /** Moves selection. Pass {@code []} to deselect. */
    setSelection(path: SelectionPath): void {
        // Defensive copy so callers can pass their own arrays freely.
        this.selection = Array.from(path);
        this.emit();
    }

    /**
     * Adds a node into a child slot. The {@code parentPath} points at the
     * container; {@code property} names the slot (e.g. {@code "fields"} or
     * {@code "node"}). For LIST slots the new node lands at the end; for
     * SINGLE slots the new node replaces whatever was there. The new node
     * becomes the selection so the user can edit it immediately.
     */
    addChild(parentPath: SelectionPath, property: string, node: UiNodeJson): void {
        if (!this.rootNode) return;
        const parent = parentPath.length === 0
            ? this.rootNode as any
            : this.rawAt(parentPath);
        if (!parent) return;
        // No id-rewriting here — duplicate ids are flagged post-mutation by
        // {@link #findDuplicateIds} so views can surface a warning without
        // blocking the operation. Id uniqueness is an authoring concern,
        // not a hard model invariant: nodes with the same id still serialise
        // and round-trip just fine.
        const existing = parent[property];
        if (Array.isArray(existing)) {
            const newIndex = existing.length;
            existing.push(node);
            this.selection = [...parentPath, property, newIndex];
        } else {
            // SINGLE slot or never-populated: just set the property.
            parent[property] = node;
            this.selection = [...parentPath, property];
        }
        this.emit();
    }

    /**
     * Walks the entire tree and returns the set of ids that appear more
     * than once. Used by the property panel and add operations to surface
     * collisions after a mutation — we don't block the mutation, just warn
     * (id uniqueness is an authoring concern, not a hard model invariant).
     */
    findDuplicateIds(): Set<string> {
        const seen = new Set<string>();
        const dups = new Set<string>();
        const walk = (node: any) => {
            if (node == null || typeof node !== "object") return;
            if (typeof node.id === "string" && node.id) {
                if (seen.has(node.id)) dups.add(node.id);
                else seen.add(node.id);
            }
            for (const key of Object.keys(node)) {
                const v = node[key];
                if (Array.isArray(v)) v.forEach(walk);
                else if (v && typeof v === "object") walk(v);
            }
        };
        walk(this.rootNode);
        return dups;
    }

    /**
     * Deletes the node at {@code path}. Three cases — exact, no guessing:
     * <ul>
     *   <li>Root path ({@code []}) — clears the whole tree.</li>
     *   <li>Numeric last hop — splices the parent array.</li>
     *   <li>String last hop — clears the named single-slot property.</li>
     * </ul>
     * <p>Callers are responsible for passing the path of the thing they want
     * gone: the tree view's trash-button on an entry-wrapper row already
     * uses the wrapper's array-index path, and the trash on a wrapper's
     * content uses the {@code [..., "<contentProp>"]} path. {@code deleteAt}
     * therefore never has to guess which one was meant — earlier "entry-
     * wrapper" heuristic clobbered the wrapper whenever the user merely
     * wanted to remove the inner node.
     * <p>Selection collapses to the parent in every case.
     */
    deleteAt(path: SelectionPath): void {
        if (path.length === 0) {
            this.rootNode = null;
            this.selection = [];
            this.emit();
            return;
        }
        const last = path[path.length - 1];
        if (typeof last === "number") {
            // List entry: splice the index out of the parent array.
            const parentPath = path.slice(0, -2);
            const property = path[path.length - 2] as string;
            const parent = parentPath.length === 0
                ? this.rootNode as any
                : this.rawAt(parentPath);
            if (!parent || !Array.isArray(parent[property])) return;
            parent[property].splice(last, 1);
            this.selection = parentPath;
        } else {
            // Single slot / wrapper-content slot: just delete the named key.
            const parentPath = path.slice(0, -1);
            const parent = parentPath.length === 0
                ? this.rootNode as any
                : this.rawAt(parentPath);
            if (!parent) return;
            delete parent[last as string];
            this.selection = parentPath;
        }
        this.emit();
    }

    /**
     * Replaces the node at {@code path} with a new JSON object — used by the
     * JSON editor and by any future property-panel field-by-field updates.
     * Preserves selection so the user keeps editing the same node.
     */
    replaceAt(path: SelectionPath, replacement: UiNodeJson): void {
        if (path.length === 0) {
            this.rootNode = replacement;
            this.emit();
            return;
        }
        const parentPath = path.slice(0, -1);
        const lastHop = path[path.length - 1];
        // rawAt because the parent may be an array (numeric last hop) or an
        // entry-wrapper without a type discriminator — both rejected by nodeAt.
        const parent = parentPath.length === 0
            ? this.rootNode as any
            : this.rawAt(parentPath);
        if (parent == null) return;
        parent[lastHop as any] = replacement;
        this.emit();
    }

    // ── Listeners ────────────────────────────────────────────────────────

    /** Returns an unsubscribe function. */
    subscribe(listener: EditorListener): () => void {
        this.listeners.add(listener);
        // Fire immediately so the subscriber paints from current state on
        // wire-up; matches the "render-on-mount" convention of modern UI libs.
        listener(this.snapshot());
        return () => this.listeners.delete(listener);
    }

    private emit(): void {
        const snap = this.snapshot();
        for (const l of this.listeners) l(snap);
    }
}

// ── Wire helpers ───────────────────────────────────────────────────────────
//
// These delegate to the active {@link EditorBackend}. The Spring-hosted editor
// uses the default {@link RestBackend} (four calls to /editor/api/*); the
// standalone app installs a localStorage-backed one. Callers (editor.ts,
// tree-view.ts, persistence.ts) are unaware of which is in play.

import { getBackend } from "./backend.js";

export function loadSchema(): Promise<Schema> {
    return getBackend().loadSchema();
}

export function loadContent(): Promise<EditorContent> {
    return getBackend().loadContent();
}

export function saveContent(content: EditorContent): Promise<void> {
    return getBackend().saveContent(content);
}

/**
 * A fresh default instance of {@code type}. With the REST backend the server
 * runs the {@code NodeRegistry} factory; with the localStorage backend a
 * bundled default (dumped from that same registry) is cloned with a fresh id.
 * Either way the node arrives with sensible ids/labels/booleans filled in.
 */
export function loadDefault(type: string): Promise<UiNodeJson> {
    return getBackend().loadDefault(type);
}
