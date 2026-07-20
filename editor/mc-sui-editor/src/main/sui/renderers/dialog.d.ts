import type { UiDialog } from "../model.js";
import { type SuiRenderer } from "../renderer.js";
/**
 * Renders a {@link UiDialog} as a fixed-position modal overlay (backdrop +
 * centred box). Because the box is fixed-positioned it overlays the viewport
 * wherever the node sits in the DOM — so a dialog is "opened" by APPENDing it
 * into the dialog host and "closed" by REMOVE-ing it by id. Several dialogs can
 * coexist; the outer host carries the dialog's own id so a REMOVE targets it.
 *
 * <p>Mirrors the SSR {@code dialog.hbs} template. The {@code data-sui-dialog-close}
 * marker on the × and the backdrop is what the event bus intercepts to REMOVE
 * this dialog (or, in SSR, the anchor's {@code closeHref} navigates).
 */
export declare function renderDialog(node: UiDialog, r: SuiRenderer): string;
