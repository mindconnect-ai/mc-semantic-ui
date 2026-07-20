import type { UiAction, UiLink, UiListItem, Pagination } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
export declare function renderActions(actions: UiAction[]): string;
export declare function renderLinks(links: UiLink[]): string;
export declare function renderPagination(p: Pagination): string;
export declare function defaultRenderItem(item: UiListItem, r: SuiRenderer): string;
