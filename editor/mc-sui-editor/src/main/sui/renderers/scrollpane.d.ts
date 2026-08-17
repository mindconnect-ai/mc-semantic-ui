import type { UiScrollPane } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * A scrolling viewport around one child. Parity with {@code scrollpane.hbs}.
 * The {@code data-stick-latest} marker is picked up by the bus's auto-enhance
 * (wireAutoScroll) for the live-feed behaviour; without JS the pane just
 * scrolls.
 */
export declare function renderScrollPane(node: UiScrollPane, r: SuiRenderer): string;
