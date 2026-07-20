import { renderIcon } from "./icon.js";

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
export function wireOverflow(root: ParentNode = document): void {
    root.querySelectorAll<HTMLElement>('[data-sui-overflow="menu"]').forEach(setup);
}

function setup(bar: HTMLElement): void {
    if (bar.dataset.suiOverflowReady === "true") return;
    bar.dataset.suiOverflowReady = "true";
    bar.classList.add("sui-overflow-ready");

    const more = document.createElement("div");
    more.className = "sui-overflow-more";
    more.hidden = true;
    more.innerHTML =
        `<button type="button" class="sui-overflow-btn" aria-haspopup="true" aria-expanded="false" aria-label="More">${renderIcon("more")}</button>` +
        `<div class="sui-overflow-menu" role="menu" hidden></div>`;
    bar.appendChild(more);

    const btn = more.querySelector<HTMLElement>(".sui-overflow-btn")!;
    const menu = more.querySelector<HTMLElement>(".sui-overflow-menu")!;

    const close = () => { menu.hidden = true; btn.setAttribute("aria-expanded", "false"); };
    const open  = () => { menu.hidden = false; btn.setAttribute("aria-expanded", "true"); };

    btn.addEventListener("click", (e) => { e.stopPropagation(); menu.hidden ? open() : close(); });
    // Entries keep their own behaviour — they are the original elements, moved.
    // We only close, then re-measure in case the selection moved back into view.
    menu.addEventListener("click", () => {
        close();
        requestAnimationFrame(() => layout(bar, more));
    });
    document.addEventListener("click", (e) => {
        if (!more.contains(e.target as Node)) close();
    });

    layout(bar, more);
    if (typeof ResizeObserver === "function") {
        new ResizeObserver(() => { close(); layout(bar, more); }).observe(bar);
    }
}

/**
 * Moves children between the bar and the dropdown until the bar fits one row.
 * Pulls everything back first so the natural width is measured, then moves from
 * the end until it fits.
 *
 * <p>Measuring relies on the overflow being on the <em>end</em> side: overflow
 * past the start edge is not scrollable, so {@code scrollWidth} would equal
 * {@code clientWidth} and this would conclude "it fits". That is why the
 * stylesheet keeps these containers start-aligned.
 */
function layout(bar: HTMLElement, more: HTMLElement): void {
    const menu = more.querySelector<HTMLElement>(".sui-overflow-menu")!;
    const itemSel = bar.dataset.suiOverflowItems;
    const activeCls = bar.dataset.suiOverflowActive || "active";

    while (menu.firstChild) bar.insertBefore(menu.firstChild, more);
    more.hidden = true;

    if (bar.scrollWidth <= bar.clientWidth) return;          // everything fits

    more.hidden = false;
    const movable = () => (Array.from(bar.children) as HTMLElement[])
        .filter(c => c !== more && (!itemSel || c.matches(itemSel)));

    let guard = 0;
    while (bar.scrollWidth > bar.clientWidth && guard++ < 100) {
        const list = movable();
        const last = list[list.length - 1];
        if (!last) break;
        menu.insertBefore(last, menu.firstChild);            // prepend keeps order
    }
    // Surface a selection that has been tucked away.
    btnOf(more).classList.toggle(activeCls, !!menu.querySelector("." + activeCls));
}

function btnOf(more: HTMLElement): HTMLElement {
    return more.querySelector<HTMLElement>(".sui-overflow-btn")!;
}
