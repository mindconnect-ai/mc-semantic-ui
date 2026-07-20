import type { UiStack } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Plain composition box: renders each child in order, no chrome. Direction
 * and gap are surfaced as CSS so the host stylesheet can override with
 * tokens. Parity with {@code stack.hbs}.
 */
export declare function renderStack(node: UiStack, r: SuiRenderer): string;
