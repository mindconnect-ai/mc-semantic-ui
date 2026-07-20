import type { UiTableRow } from "../model.js";
/**
 * Standalone render of a single {@link UiTableRow}. Inside a real table
 * the row is a {@code <tr>}; outside one it's a key/value list of the
 * row's data fields. Useful for the editor preview when the user selects
 * a row in the tree.
 */
export declare function renderRow(node: UiTableRow): string;
