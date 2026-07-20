import type { UiSectionEntry } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Standalone render of a single {@link UiSectionEntry}. Normally entries
 * are rendered inline by the section's tab+panel template, but the editor
 * preview can address a single entry directly when the user selects it in
 * the tree. We emit a labelled box with the entry's title above its content
 * — enough context to read the structure without surrounding tabs.
 */
export declare function renderSectionEntry(node: UiSectionEntry, r: SuiRenderer): string;
