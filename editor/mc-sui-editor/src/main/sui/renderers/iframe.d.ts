import type { UiIFrame } from "../model.js";
/**
 * An embedded browsing context. Parity with {@code iframe.hbs}: a height
 * caps the frame in normal flow, without one it fills a flex-column parent
 * (the .sui-iframe default in sui.css).
 */
export declare function renderIFrame(node: UiIFrame): string;
