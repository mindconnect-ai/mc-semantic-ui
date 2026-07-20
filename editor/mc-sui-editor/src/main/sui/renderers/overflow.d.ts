/**
 * Priority-plus overflow for any single-row container.
 *
 * <p>Mark a container with {@code data-sui-overflow="menu"} and its children
 * that don't fit collapse into a trailing "⋯" dropdown, re-computed on resize.
 * Tab bars and header extras both use it; so can anything you build.
 *
 * <p>Configuration is declarative, in data attributes, so this module needs no
 * knowledge of the things it lays out:
 *
 * <ul>
 *   <li>{@code data-sui-overflow="menu"} — opt in. Any other value (or none)
 *       is left alone, which is how {@code WRAP} stays the default.</li>
 *   <li>{@code data-sui-overflow-items="<selector>"} — which children may be
 *       moved. Defaults to every element child.</li>
 *   <li>{@code data-sui-overflow-active="<class>"} — when a moved child carries
 *       this class, the "⋯" button gets it too, so a hidden-but-selected entry
 *       is still visible. Defaults to {@code active}.</li>
 * </ul>
 *
 * <p><b>Progressive enhancement.</b> The server renders a plain container;
 * without this wiring (or without JavaScript) the children simply wrap, which
 * is the CSS default. Nothing is ever unreachable — the dropdown is an
 * improvement, never a requirement.
 *
 * <p>The {@link SuiEventBus} calls this on every mount and DOM change, so
 * application code normally doesn't. Idempotent per container.
 */
export declare function wireOverflow(root?: ParentNode): void;
