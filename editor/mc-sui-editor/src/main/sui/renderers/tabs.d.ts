/**
 * Tab-bar overflow. Kept as a named export because it is part of the public
 * API, but the behaviour itself is the shared {@link wireOverflow} — a tab bar
 * is just a container marked {@code data-sui-overflow="menu"} whose movable
 * children are {@code .sui-tab}.
 *
 * @deprecated Prefer {@link wireOverflow}, which covers every container at
 * once. The event bus calls it automatically on mount.
 */
export declare function wireTabOverflow(root?: ParentNode): void;
