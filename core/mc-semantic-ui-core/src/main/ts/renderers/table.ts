import type { UiAction, UiNode, UiTable, UiTrigger } from "../model.js";
import { escapeHtml, encodeTrigger, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";
import { renderActions, renderPagination } from "./shared.js";
import { renderAction } from "./action.js";

export function renderTable(node: UiTable, r: SuiRenderer): string {
    const cols = node.columns || [];
    const rows = node.rows || [];
    const rowActions = node.rowActions || [];
    const selectMode = node.selectMode ?? "NONE";
    // All selection inputs in a table share one name so the surrounding
    // form submits "<table.id>__selection=<row.id>" for SINGLE, or repeated
    // entries for MULTI. The wrapper-form is the caller's responsibility.
    const selectionName = `${node.id}__selection`;
    const selectedIds = new Set<string>(node.selectedRowIds ?? []);

    // <th> for the selection column gets no header label by convention.
    const selectionTh = selectMode !== "NONE" ? `<th class="sui-table-selection"></th>` : "";
    // Each <th> carries the column's UiNode id so the editor's
    // selection-by-id lookup (highlight + click → tree) finds it without
    // needing to know about column-specific DOM shape.
    const thead = selectionTh + cols.map(c => {
        const idAttr = c.id ? ` id="${escapeHtml(c.id)}"` : "";
        if (!c.sortable) return `<th${idAttr}>${escapeHtml(c.label ?? "")}</th>`;
        return `<th${idAttr}${sortAttrs(node, c)}>${sortControl(node, c)}</th>`;
    }).join("") + (rowActions.length ? "<th></th>" : "");

    const tbody = rows.map(row => {
        const data = row.data ?? {};
        const isSelected = node.selectedRowId != null && row.id === node.selectedRowId;
        // Substitution context for cellTemplate: row data plus row.id as {id}.
        const ctx: Record<string, unknown> = { ...data, id: row.id ?? data["id"] };
        const rowSuffix = String(row.id ?? data["id"] ?? "");
        // Optional leading selection cell (radio/checkbox).
        const rowKey = String(row.id ?? data["id"] ?? "");
        const checked = selectedIds.has(rowKey) ? " checked" : "";
        const selectionCell = selectMode === "SINGLE"
            ? `<td class="sui-table-selection"><input type="radio" name="${escapeHtml(selectionName)}" value="${escapeHtml(rowKey)}"${checked}></td>`
            : selectMode === "MULTI"
            ? `<td class="sui-table-selection"><input type="checkbox" name="${escapeHtml(selectionName)}" value="${escapeHtml(rowKey)}"${checked}></td>`
            : "";
        const cells = cols.map(c => {
            const key = c.dataKey ?? c.id;
            // data-label lets the stacked-card mobile layout show "Column: value".
            const label = c.label ? ` data-label="${escapeHtml(c.label)}"` : "";
            if (c.cellTemplate) {
                // Clone, substitute, suffix ids, then dispatch through the
                // renderer so handlers (link, action, text, …) take over.
                const cloned = substituteCellTemplate(c.cellTemplate, ctx, rowSuffix);
                return `<td${label}>${r.render(cloned)}</td>`;
            }
            const v = data[key];
            return `<td${label}>${v != null ? escapeHtml(v) : ""}</td>`;
        }).join("");
        // Row-actions get a context object that surfaces both the row's own
        // {@code id} (UiNode-level) and its {@code data} map — that way the
        // {id} substitution in trigger templates works whether the id was
        // promoted to the UiRow or lives in row.data.
        const rowCtx: Record<string, unknown> = { ...data, id: row.id ?? data["id"] };
        const actionCells = rowActions.length
            ? `<td class="sui-table-row-actions">${renderRowActions(rowActions, rowCtx)}</td>`
            : "";
        // <tr id="..."> so the editor's selection-by-id mechanism can address
        // the row directly — same reason <th> got an id above.
        const idAttr = row.id ? ` id="${escapeHtml(row.id)}"` : "";
        const rowCls = isSelected ? ' class="sui-row--selected" data-selected="true"' : "";
        return `<tr${idAttr}${rowCls}>${selectionCell}${cells}${actionCells}</tr>`;
    }).join("");

    const colCount = cols.length + (rowActions.length ? 1 : 0) + (selectMode !== "NONE" ? 1 : 0);
    const emptyRow = rows.length === 0
        ? `<tr><td colspan="${colCount}" class="sui-table-empty">No rows.</td></tr>`
        : "";

    // The table carries its own serialised model so the patch pipeline can
    // treat row/column patches as model updates: REPLACE/REMOVE a <tr>/<th>
    // (or APPEND a row) edits this JSON and re-renders the whole table —
    // the only way a single column swap can update every cell consistently.
    // Same data-node convention as renderForm.
    const nodeJson = escapeHtml(JSON.stringify(node));
    const stackCls = node.stackOnMobile ? "sui-table sui-table--stack" : "sui-table";
    // The header bar carries the title AND the header-level actions. Gating
    // it on the title alone would silently swallow the actions of a
    // title-less table, so either one is enough to render the bar.
    const actionsHtml = renderActions(node.actions || []);
    const header = (node.title || actionsHtml)
        ? `<div class="sui-table-header">${node.title ? `<h2>${escapeHtml(node.title)}</h2>` : ""}${actionsHtml}</div>`
        : "";
    // maxHeight turns the table body into its own scroll container; the CSS
    // pins <thead> with position:sticky so the header stays visible.
    const scrollStyle = node.maxHeight ? ` style="max-height:${escapeHtml(node.maxHeight)}"` : "";
    const scrollCls = node.maxHeight ? "sui-table-scroll sui-table-scroll--capped" : "sui-table-scroll";
    return `<div class="${cls(stackCls, node)}"${evt(node)} id="${escapeHtml(node.id)}" data-sui="table" data-node='${nodeJson}'>
        ${header}
        <div class="${scrollCls}"${scrollStyle}>
        <table>
            <thead><tr>${thead}</tr></thead>
            <tbody>${tbody}${emptyRow}</tbody>
        </table>
        </div>
        ${node.pagination ? renderPagination(node.pagination) : ""}
    </div>`;
}

/** `aria-sort` + a marker class for the currently sorted column. */
function sortAttrs(node: UiTable, c: { dataKey?: string; id?: string }): string {
    const key = c.dataKey ?? c.id ?? "";
    const active = node.sortColumn != null && node.sortColumn === key;
    const dir = (node.sortDirection ?? "ASC") === "DESC" ? "descending" : "ascending";
    return ` class="sui-th-sortable${active ? " is-sorted" : ""}" aria-sort="${active ? dir : "none"}"`;
}

/**
 * The clickable header. With a {@code sortTrigger} the button carries a
 * per-column `data-trigger` (server sorts, exactly like pagination); without
 * one it carries only `data-sui-sort`, which the event bus picks up for a
 * client-side reorder of the rows already on screen.
 */
function sortControl(node: UiTable, c: { dataKey?: string; id?: string; label?: string }): string {
    const key = c.dataKey ?? c.id ?? "";
    const active = node.sortColumn != null && node.sortColumn === key;
    const currentDir = (node.sortDirection ?? "ASC") === "DESC" ? "DESC" : "ASC";
    // Clicking the active column flips it; a fresh column starts ascending.
    const nextDir = active && currentDir === "ASC" ? "DESC" : "ASC";
    const indicator = active ? (currentDir === "ASC" ? "↑" : "↓") : "↕";
    const trigger = node.sortTrigger
        ? ` data-trigger='${encodeTrigger({
            ...node.sortTrigger,
            url: (node.sortTrigger.url ?? "")
                .replace(/\{column\}/g, encodeURIComponent(key))
                .replace(/\{direction\}/g, nextDir.toLowerCase()),
        })}'`
        : "";
    return `<button type="button" class="sui-sort-btn" data-sui-sort="${escapeHtml(key)}"`
        + ` data-sui-sort-dir="${nextDir}"${trigger}>`
        + `${escapeHtml(c.label ?? "")}<span class="sui-sort-indicator" aria-hidden="true">${indicator}</span>`
        + `</button>`;
}

function renderRowActions(actions: UiAction[], row: Record<string, unknown>): string {
    const rowId = String(row["id"] ?? "");
    return actions.map(a => {
        // Row actions share a trigger template across all rows. We substitute
        // {id} (and any future row-scoped placeholders) per render so each
        // rendered button carries the row's own target. We also suffix the
        // action's DOM id with the row id so the rendered <button id=...>
        // stays unique across rows (browsers complain about duplicate ids,
        // and the editor's id-based selection would otherwise pick the
        // wrong row's button).
        const perRowId = rowId ? `${a.id}__${rowId}` : a.id;
        const personal: UiTrigger | undefined = a.onClick
            ? { ...a.onClick, url: a.onClick.url?.replace("{id}", rowId) }
            : undefined;
        return renderAction({ ...a, id: perRowId, onClick: personal });
    }).join("");
}

/**
 * Clones {@code template} deeply, runs every string field of every nested
 * node through {@code {dataKey}}-substitution against {@code ctx}, and
 * suffixes every {@code id} with {@code __<rowSuffix>} so the rendered
 * cell-subtree's DOM ids stay unique across rows. Unknown substitution
 * keys are left intact ({@code {xyz}}) so the author sees the broken
 * placeholder rather than silent failure.
 */
function substituteCellTemplate(template: UiNode, ctx: Record<string, unknown>, rowSuffix: string): UiNode {
    const subst = (s: string): string => s.replace(/\{([^{}]+)\}/g, (m, key) => {
        if (!(key in ctx)) return m;
        const v = ctx[key];
        return v == null ? "" : String(v);
    });
    const walk = (node: any): any => {
        if (node == null) return node;
        if (Array.isArray(node)) return node.map(walk);
        if (typeof node === "string") return subst(node);
        if (typeof node !== "object") return node;
        const out: any = {};
        for (const key of Object.keys(node)) {
            const v = (node as any)[key];
            if (key === "id" && typeof v === "string" && rowSuffix) {
                out[key] = `${v}__${rowSuffix}`;
            } else {
                out[key] = walk(v);
            }
        }
        return out;
    };
    return walk(template) as UiNode;
}
