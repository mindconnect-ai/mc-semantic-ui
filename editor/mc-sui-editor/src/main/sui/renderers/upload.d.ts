import type { UiUpload } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Renders a {@link UiUpload} drop zone: a hidden {@code <input type="file">}
 * plus a styled area the user can drop files onto, with a browse button (a
 * {@code <label for>} that opens the native picker without JS).
 *
 * <p>The upload trigger is emitted as {@code data-upload-trigger} — a
 * deliberately separate attribute from the click-owned {@code data-trigger} so
 * clicking the zone doesn't fire it; the {@code SuiEventBus} reads it on
 * {@code drop} and on the input's {@code change} (see the upload handling in
 * {@code eventbus.ts}). The multipart field name rides along in
 * {@code data-sui-upload-name}.
 */
export declare function renderUpload(node: UiUpload, _r: SuiRenderer): string;
