import type { UiLink } from "../model.js";
/**
 * Renders a single {@link UiLink} as a standalone {@code <a>}. The wrapper
 * id="…" follows the same convention as every other node type, so the
 * editor's id-based selection finds it. {@code href} drives the navigation;
 * {@code data-href} is a hint the EventBus reads to route via the SPA when
 * present.
 *
 * <p>Cast through {@code any} for {@code id} because the TS {@link UiLink}
 * interface omits the UiNode-inherited id field (Java-side it's there).
 * The runtime payload always carries it from the server.
 */
export declare function renderLink(node: UiLink): string;
