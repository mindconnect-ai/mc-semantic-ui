/*
 * Draggable split panes for the editor's three-pane layout.
 *
 * The layout is a pair of CSS grids: {@code .sui-editor-main} splits Tree |
 * Right by column, {@code .sui-editor-right} splits Properties | Preview by
 * row. Each split has a gutter element ({@code [data-splitter]}) sitting in a
 * dedicated grid track. Dragging a gutter rewrites a CSS custom property on the
 * grid container ({@code --tree-w} / {@code --panel-h}), which the
 * grid-template lines read — so resizing is just moving one number.
 *
 * Sizes persist to localStorage so the chosen layout survives reloads and
 * page switches. No dependency, pointer-events based (mouse + touch).
 */

const STORAGE_KEY = "sui-editor:layout";

interface SplitSpec {
    /** The gutter's {@code data-splitter} value. */
    key: "col" | "row";
    /** Grid container whose CSS var the gutter drives. */
    container: HTMLElement;
    /** CSS custom property to write (e.g. "--tree-w"). */
    cssVar: string;
    /** "x" for a column split (width), "y" for a row split (height). */
    axis: "x" | "y";
    min: number;
    max: number;
}

type Saved = { treeW?: number; panelH?: number };

function loadSaved(): Saved {
    try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}") as Saved; }
    catch { return {}; }
}

function save(patch: Saved): void {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...loadSaved(), ...patch })); }
    catch { /* storage disabled — sizes just won't persist */ }
}

/**
 * Wires the gutters found under {@code root}. Call once after the layout HTML
 * is in place. Restores any previously-saved sizes first.
 */
export function installSplitters(root: HTMLElement): void {
    const main = root.querySelector<HTMLElement>(".sui-editor-main");
    const right = root.querySelector<HTMLElement>(".sui-editor-right");
    if (!main || !right) return;

    const specs: SplitSpec[] = [
        { key: "col", container: main, cssVar: "--tree-w", axis: "x", min: 160, max: 640 },
        { key: "row", container: right, cssVar: "--panel-h", axis: "y", min: 120, max: 10_000 },
    ];

    // Restore saved sizes.
    const saved = loadSaved();
    if (saved.treeW != null) main.style.setProperty("--tree-w", `${saved.treeW}px`);
    if (saved.panelH != null) right.style.setProperty("--panel-h", `${saved.panelH}px`);

    for (const spec of specs) {
        const gutter = root.querySelector<HTMLElement>(`[data-splitter="${spec.key}"]`);
        if (gutter) wire(gutter, spec);
    }
}

function wire(gutter: HTMLElement, spec: SplitSpec): void {
    // Move/up are attached to the document only while dragging, so the drag
    // keeps tracking even when the pointer runs off the thin gutter (or off the
    // window). No pointer-capture needed — which is both simpler and more
    // robust than capturing on the gutter element.
    const onMove = (e: PointerEvent) => {
        const rect = spec.container.getBoundingClientRect();
        const raw = spec.axis === "x" ? e.clientX - rect.left : e.clientY - rect.top;
        // Keep the far pane usable too: cap at container size minus a margin.
        const ceiling = Math.min(spec.max, (spec.axis === "x" ? rect.width : rect.height) - 120);
        const size = Math.round(clamp(raw, spec.min, ceiling));
        spec.container.style.setProperty(spec.cssVar, `${size}px`);
    };

    const onUp = () => {
        document.removeEventListener("pointermove", onMove);
        document.body.classList.remove("sui-editor-resizing");
        // Persist the final size (parsed back from the CSS var).
        const px = parseInt(spec.container.style.getPropertyValue(spec.cssVar), 10);
        if (!Number.isNaN(px)) save(spec.key === "col" ? { treeW: px } : { panelH: px });
    };

    gutter.addEventListener("pointerdown", (e) => {
        e.preventDefault();
        document.body.classList.add("sui-editor-resizing");
        document.addEventListener("pointermove", onMove);
        document.addEventListener("pointerup", onUp, { once: true });
        document.addEventListener("pointercancel", onUp, { once: true });
    });

    // Double-click resets this split to its CSS default.
    gutter.addEventListener("dblclick", () => {
        spec.container.style.removeProperty(spec.cssVar);
        save(spec.key === "col" ? { treeW: undefined } : { panelH: undefined });
    });
}

function clamp(v: number, min: number, max: number): number {
    return Math.max(min, Math.min(max, v));
}
