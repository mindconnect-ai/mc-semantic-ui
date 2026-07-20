import type { UiFieldGroup } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Renders a {@link UiFieldGroup} as a native {@code <fieldset>} with a
 * {@code <legend>} title, so the group heading is associated with every field
 * inside. Children go through the dispatcher, so a group can hold fields or any
 * layout node. Parity with {@code fieldgroup.hbs}.
 */
export declare function renderFieldGroup(node: UiFieldGroup, r: SuiRenderer): string;
