/**
 * Header-extras overflow. A header's extras bar is a container marked
 * {@code data-sui-overflow="menu"}, so the shared {@link wireOverflow}
 * behaviour handles it — this alias exists only for discoverability.
 *
 * @deprecated Prefer {@link wireOverflow}; the event bus calls it for you.
 */
export declare function wireHeaderOverflow(root?: ParentNode): void;
