/*
 * Semantic UI editor — bootstrap.
 *
 * Loads the schema + initial content from the active {@link EditorBackend},
 * paints the toolbar and the three panes, then hands ownership of each pane to
 * its component (TreeView, PropertyPanel, Preview). All three components share
 * a single {@link EditorState}; user actions flow through it and listeners
 * fan-out the resulting snapshot to whoever needs to redraw.
 *
 * The editor itself is frontend-only between saves: mutations live in the
 * browser, the user clicks Save when they want to persist them. This keeps the
 * wire format honest — we only save a well-formed UiNode tree — and lets the
 * user experiment freely.
 *
 * Two entry points:
 *  - the module auto-boots against {@code #sui-editor-root} with the default
 *    REST backend, which is exactly what the Spring-hosted editor.html wants;
 *  - {@link bootEditor} lets an embedder (the standalone app) mount the editor
 *    into any host with any backend and an optional "back" affordance.
 */
import { EditorState, loadContent, loadSchema } from "./editor-state.js";
import { setBackend, type EditorBackend } from "./backend.js";
import { SaveManager } from "./persistence.js";
import { Preview } from "./preview.js";
import { PropertyPanel } from "./property-panel.js";
import { TreeView } from "./tree-view.js";
import { installSplitters } from "./splitter.js";

const ROOT_ID = "sui-editor-root";

export interface BootEditorOptions {
    /** Element id to mount into. Defaults to {@code sui-editor-root}. */
    rootId?: string;
    /** Backend to install before loading. Defaults to the REST backend. */
    backend?: EditorBackend;
    /** When set, the toolbar shows a "back" button that invokes this. */
    onExit?: () => void;
    /**
     * When set, the toolbar shows a "Preview" button. The editor saves the
     * current tree first (so the preview reflects what's on screen) and then
     * invokes this — the embedder mounts a live preview of the page.
     */
    onPreview?: () => void;
    /** Toolbar heading. Defaults to "SUI Editor". */
    title?: string;
}

// The Cmd/Ctrl+S handler is registered once and always targets whichever
// editor instance booted most recently. bootEditor may run many times (the
// standalone app re-mounts when switching pages); without this a listener
// would leak on every mount.
let currentSaver: SaveManager | null = null;
let saveShortcutInstalled = false;

/**
 * Boots an editor instance into {@code opts.rootId}. Returns once the panes
 * are wired and the initial tree is loaded. Safe to call repeatedly against
 * different hosts (the standalone app re-mounts when switching pages).
 */
export async function bootEditor(opts: BootEditorOptions = {}): Promise<void> {
    if (opts.backend) setBackend(opts.backend);
    const rootId = opts.rootId ?? ROOT_ID;
    const root = document.getElementById(rootId);
    if (!root) {
        console.error(`SUI editor: no #${rootId} element on the page`);
        return;
    }

    // Paint the static layout first so the user sees the shell while we
    // fetch state. The component constructors take over their hosts once
    // we have the state object ready.
    root.innerHTML = layoutHtml(opts.title ?? "SUI Editor", !!opts.onExit, !!opts.onPreview);
    installSplitters(root);
    const statusEl = root.querySelector<HTMLElement>(".sui-editor-status")!;
    const saveBtn = root.querySelector<HTMLButtonElement>(".sui-editor-save")!;
    const exitBtn = root.querySelector<HTMLButtonElement>(".sui-editor-exit");
    const previewBtn = root.querySelector<HTMLButtonElement>(".sui-editor-preview-btn");
    const treeHost = root.querySelector<HTMLElement>(".sui-editor-tree")!;
    const panelHost = root.querySelector<HTMLElement>(".sui-editor-panel")!;
    const previewHost = root.querySelector<HTMLElement>(".sui-editor-preview")!;

    if (exitBtn && opts.onExit) {
        exitBtn.addEventListener("click", () => opts.onExit!());
    }

    statusEl.textContent = "Loading…";
    let schema, content;
    try {
        [schema, content] = await Promise.all([loadSchema(), loadContent()]);
    } catch (err) {
        statusEl.textContent = "Failed to load editor state — see console.";
        console.error("SUI editor: bootstrap failed", err);
        return;
    }

    const state = new EditorState(schema);
    // Wire the views BEFORE seeding the root so their subscribe-on-mount
    // call gets a consistent (empty) snapshot first, then the real one.
    new TreeView(treeHost, state);
    new PropertyPanel(panelHost, state);
    new Preview(previewHost, state);
    state.replaceRoot(content.root ?? null);

    // Save manager: tracks the dirty bit and handles the manual save. The
    // toolbar status updates in lock-step.
    const saver = new SaveManager(state);
    saver.onStatus((status) => {
        const label = status === "saving" ? "Saving…"
                    : status === "dirty"  ? "Unsaved changes"
                    : status === "error"  ? "Save failed — click Save to retry"
                                          : "Saved";
        statusEl.textContent = label;
        statusEl.dataset.status = status;
        // Disable the button only during the actual save — clicking it while
        // idle/dirty/error is what the user is supposed to do.
        saveBtn.disabled = status === "saving";
    });
    saveBtn.addEventListener("click", () => void saver.save());

    if (previewBtn && opts.onPreview) {
        previewBtn.addEventListener("click", async () => {
            await saver.save();
            opts.onPreview!();
        });
    }

    // Keyboard shortcut for the save button: Cmd/Ctrl+S. Registered once and
    // routed to the current editor instance (see currentSaver above). Prevent
    // the browser's "save page" dialog which would otherwise eat the gesture.
    currentSaver = saver;
    if (!saveShortcutInstalled) {
        saveShortcutInstalled = true;
        document.addEventListener("keydown", (e) => {
            if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "s") {
                e.preventDefault();
                void currentSaver?.save();
            }
        });
    }

    console.info("SUI editor: ready");
}

function layoutHtml(title: string, withExit: boolean, withPreview: boolean): string {
    const exit = withExit
        ? `    <button type="button" class="sui-editor-exit sui-btn">← Projects</button>`
        : ``;
    const preview = withPreview
        ? `    <button type="button" class="sui-editor-preview-btn sui-btn">▶ Preview</button>`
        : ``;
    return (
        `<div class="sui-editor-toolbar">` +
        exit +
        `  <h1>${escapeHtml(title)}</h1>` +
        `  <span class="sui-editor-status">Booting…</span>` +
        `  <div class="sui-editor-actions">` +
        preview +
        `    <button type="button" class="sui-editor-save sui-btn sui-btn--primary">Save</button>` +
        `  </div>` +
        `</div>` +
        `<div class="sui-editor-main">` +
        `  <aside class="sui-editor-tree-pane">` +
        `    <h2>Tree</h2>` +
        `    <div class="sui-editor-tree"></div>` +
        `  </aside>` +
        `  <div class="sui-editor-gutter sui-editor-gutter--col" data-splitter="col" title="Drag to resize · double-click to reset"></div>` +
        `  <section class="sui-editor-right">` +
        `    <div class="sui-editor-panel-pane">` +
        `      <h2>Properties</h2>` +
        `      <div class="sui-editor-panel"></div>` +
        `    </div>` +
        `    <div class="sui-editor-gutter sui-editor-gutter--row" data-splitter="row" title="Drag to resize · double-click to reset"></div>` +
        `    <div class="sui-editor-preview-pane">` +
        `      <h2>Preview</h2>` +
        `      <div class="sui-editor-preview"></div>` +
        `    </div>` +
        `  </section>` +
        `</div>`
    );
}

function escapeHtml(value: string): string {
    return value.replace(/[&<>"]/g, (c) =>
        c === "&" ? "&amp;" : c === "<" ? "&lt;" : c === ">" ? "&gt;" : "&quot;");
}

// Auto-boot for the Spring-hosted editor.html, which ships a #sui-editor-root
// and loads this module directly. When the element is absent (e.g. an embedder
// imports bootEditor and mounts elsewhere), we stay quiet — importing the
// module must have no side effects for those callers.
function autoBoot(): void {
    if (document.getElementById(ROOT_ID)) void bootEditor();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", autoBoot);
} else {
    autoBoot();
}
