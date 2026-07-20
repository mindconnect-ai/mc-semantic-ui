import type { UiTableColumn } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Standalone render of a single {@link UiTableColumn}. Inside a real
 * table the column is just a {@code <th>}; outside one it's a stub — the
 * editor preview uses this to show the user what they selected when they
 * click a column in the tree. Shows label + dataKey + the cellTemplate
 * (if any) so the author sees their per-cell template even without rows.
 */
export declare function renderColumn(node: UiTableColumn, r: SuiRenderer): string;
