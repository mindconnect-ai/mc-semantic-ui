/*
 * Tree view of the UiNode tree. Click on a row selects; click on a
 * group's plus-button opens the type picker; click on a row's trash
 * deletes the node.
 *
 * Mutations go through the shared {@link EditorState}; views re-render on
 * the resulting snapshot. The tree never holds local state of its own.
 */
import { pickType } from "./add-picker.js";
import { loadDefault } from "./editor-state.js";
import type { EditorState, EditorSnapshot, SelectionPath } from "./editor-state.js";
import type { ChildrenMeta, NodeMeta, Schema, UiNodeJson } from "./types.js";

export class TreeView {
    constructor(private readonly host: HTMLElement, private readonly state: EditorState) {
        this.host.classList.add("sui-tree");
        this.host.addEventListener("click", (e) => void this.onClick(e));
        this.state.subscribe((snap) => this.render(snap));
    }

    private render(snap: EditorSnapshot): void {
        if (!snap.root) {
            this.host.innerHTML =
                `<p class="sui-editor-panel-empty">No content yet.</p>` +
                `<button class="sui-btn sui-btn--primary" data-action="set-root">+ Add root node</button>`;
            return;
        }
        this.host.innerHTML = this.renderNode(snap.root, snap.schema, [], snap.selection);
    }

    private renderNode(node: UiNodeJson, schema: Schema, path: SelectionPath, selection: SelectionPath): string {
        const meta = schema[node.type];
        const id = typeof node.id === "string" ? node.id : "";
        const label = labelFor(node);
        const isSelected = samePath(path, selection);

        const groups: string[] = [];
        if (meta?.children?.length) {
            for (const c of meta.children) {
                groups.push(this.renderChildGroup(node, schema, c, path, selection));
            }
        }

        // Each row carries the path so the click handler can address it
        // without walking the DOM back up. Every node — including the root —
        // gets a trash button: deleting the root clears the tree and the
        // "+ Add root node" prompt returns, which is how you swap the root for
        // a different type.
        const isRoot = path.length === 0;
        const trash =
            `<button class="sui-tree-action" data-action="delete" data-path='${escapeAttr(JSON.stringify(path))}' ` +
            `title="${isRoot ? "Delete the root (clears the page)" : "Delete"}">×</button>`;

        const row = `<div class="sui-tree-node${isSelected ? " is-selected" : ""}" data-path='${escapeAttr(JSON.stringify(path))}'>` +
            `<span class="sui-tree-type">${escapeHtml(node.type)}</span>` +
            (id ? `<span class="sui-tree-id">#${escapeHtml(id)}</span>` : "") +
            (label ? `<span class="sui-tree-label">${escapeHtml(label)}</span>` : "") +
            `<span class="sui-tree-row-actions">${trash}</span>` +
            `</div>`;

        return row + groups.join("");
    }

    private renderChildGroup(node: UiNodeJson, schema: Schema, group: ChildrenMeta, parentPath: SelectionPath, selection: SelectionPath): string {
        const value = node[group.property];
        const addAttrs = `data-action="add" data-parent-path='${escapeAttr(JSON.stringify(parentPath))}' data-property='${escapeAttr(group.property)}'`;

        if (group.cardinality === "SINGLE") {
            // Single-slot child: render at most one child entry beneath the
            // label; the +-button only shows when the slot is empty (adding
            // would replace, which is surprising — the user can delete the
            // existing child first if they want a different one).
            const filled = isUiNode(value);
            const addBtn = filled
                ? ""
                : `<button class="sui-tree-action" ${addAttrs} title="Set ${escapeHtml(group.property)}">+</button>`;
            const child = filled
                ? `<ul><li>${this.renderNode(value as UiNodeJson, schema, [...parentPath, group.property], selection)}</li></ul>`
                : "";
            return (
                `<div class="sui-tree-group-label">` +
                `  <span>${escapeHtml(group.property)}</span>` +
                `  ${addBtn}` +
                `</div>` +
                child
            );
        }

        // LIST cardinality: every entry is a UiNode (UiSectionEntry,
        // UiColumn, UiRow, UiField, …). Render each as a normal tree row;
        // their own child groups (e.g. UiSectionEntry.content) recurse via
        // renderNode → renderChildGroup.
        const list = value;
        const hasChildren = Array.isArray(list) && list.length > 0;
        const items = hasChildren
            ? (list as unknown[])
                .map((child, i) => {
                    if (!isUiNode(child)) return "";
                    return `<li>${this.renderNode(child, schema, [...parentPath, group.property, i], selection)}</li>`;
                })
                .join("")
            : "";

        return (
            `<div class="sui-tree-group-label">` +
            `  <span>${escapeHtml(group.property)}</span>` +
            `  <button class="sui-tree-action" ${addAttrs} title="Add ${escapeHtml(group.property)}">+</button>` +
            `</div>` +
            (hasChildren ? `<ul>${items}</ul>` : "")
        );
    }

    private async onClick(e: MouseEvent): Promise<void> {
        const target = e.target as HTMLElement | null;
        if (!target) return;

        const setRootBtn = target.closest<HTMLElement>("[data-action='set-root']");
        if (setRootBtn) { await this.setRoot(); return; }

        const deleteBtn = target.closest<HTMLElement>("[data-action='delete']");
        if (deleteBtn) {
            e.stopPropagation();
            const raw = deleteBtn.dataset.path;
            if (!raw) return;
            const path = JSON.parse(raw) as SelectionPath;
            const msg = path.length === 0
                ? "Delete the root node? This clears the page — you can then add a different root."
                : "Delete this node?";
            if (window.confirm(msg)) this.state.deleteAt(path);
            return;
        }

        const addBtn = target.closest<HTMLElement>("[data-action='add']");
        if (addBtn) {
            e.stopPropagation();
            const parentRaw = addBtn.dataset.parentPath;
            const property = addBtn.dataset.property;
            if (!parentRaw || !property) return;
            const parentPath = JSON.parse(parentRaw) as SelectionPath;
            await this.addChild(parentPath, property);
            return;
        }

        // Otherwise: selecting a row.
        const row = target.closest<HTMLElement>(".sui-tree-node");
        if (!row || !this.host.contains(row)) return;
        const raw = row.dataset.path;
        if (raw == null) return;
        try {
            const path = JSON.parse(raw) as SelectionPath;
            this.state.setSelection(path);
        } catch (err) {
            console.error("TreeView: bad data-path", err, raw);
        }
    }

    private async setRoot(): Promise<void> {
        const allTypes = Object.values(this.state.schemaMap) as NodeMeta[];
        const chosen = await pickType(allTypes, "Pick a root node");
        if (!chosen) return;
        const node = await loadDefault(chosen);
        this.state.replaceRoot(node);
    }

    private async addChild(parentPath: SelectionPath, property: string): Promise<void> {
        const parent = this.state.nodeAt(parentPath);
        if (!parent) return;
        const parentMeta = this.state.schemaMap[parent.type];
        const group = parentMeta?.children?.find(c => c.property === property);
        if (!group) return;
        const allowed = group.allowedTypes
            .map(t => this.state.schemaMap[t])
            .filter((m): m is NodeMeta => !!m);
        const chosen = allowed.length === 1
            ? allowed[0].type
            : await pickType(allowed, `Add to ${property}`);
        if (!chosen) return;
        const node = await loadDefault(chosen);
        this.state.addChild(parentPath, property, node);
    }
}

// ── helpers ────────────────────────────────────────────────────────────────

function labelFor(node: UiNodeJson): string {
    if (typeof node.title === "string" && node.title) return node.title;
    if (typeof node.label === "string" && node.label) return node.label;
    if (typeof node.brand === "string" && node.brand) return node.brand;
    return "";
}

function isUiNode(value: unknown): value is UiNodeJson {
    return value != null && typeof value === "object" && typeof (value as any).type === "string";
}

function samePath(a: SelectionPath, b: SelectionPath): boolean {
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
    return true;
}

function escapeHtml(s: string): string {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
function escapeAttr(s: string): string {
    return s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
