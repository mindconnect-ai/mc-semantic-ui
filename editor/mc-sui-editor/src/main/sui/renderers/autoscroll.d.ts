/**
 * Progressive enhancement for live feeds (chat threads, run logs): any
 * container carrying the {@code sui-autoscroll} class sticks to its newest
 * entry — as long as the user is at (or near) the bottom, new content keeps
 * the view pinned there. The moment they scroll up to read, sticking stops
 * and a floating jump-to-latest arrow appears; clicking it (or scrolling
 * back down) re-arms the stick.
 *
 * <p>The scrolling element is the marked container itself when it scrolls,
 * else its first scrollable descendant (a chat list's {@code <ul>}). Wiring
 * is idempotent per element — the auto-enhance pass re-runs after every
 * patch, so a REPLACEd feed gets re-wired automatically.
 */
export declare function wireAutoScroll(root: ParentNode): void;
