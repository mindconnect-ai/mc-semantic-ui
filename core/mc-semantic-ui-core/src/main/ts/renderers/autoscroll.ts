import { renderIcon } from "./icon.js";

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
export function wireAutoScroll(root: ParentNode): void {
    root.querySelectorAll<HTMLElement>(".sui-autoscroll, .sui-scrollpane[data-stick-latest]").forEach(el => {
        if ((el as any).__suiAutoscroll) return;
        const scroller = findScroller(el);
        if (!scroller) return;
        (el as any).__suiAutoscroll = true;

        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "sui-scroll-latest";
        btn.setAttribute("aria-label", "Scroll to latest");
        btn.innerHTML = renderIcon("arrow-down");
        if (scroller === el) {
            // The marked element scrolls itself (UiScrollPane): an absolute
            // button would scroll along with the content, so the arrow rides
            // in a zero-height sticky host pinned to the pane's lower edge.
            const host = document.createElement("div");
            host.className = "sui-scroll-latest-host";
            host.appendChild(btn);
            el.appendChild(host);
        } else {
            // Marker on a non-scrolling wrapper: float over it directly.
            el.appendChild(btn);
        }

        const nearBottom = () =>
            scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < 60;
        const toBottom = (smooth: boolean) =>
            scroller.scrollTo({ top: scroller.scrollHeight, behavior: smooth ? "smooth" : "auto" });
        const sync = () => btn.classList.toggle("is-visible", !nearBottom());

        // Stick starts armed: a freshly opened feed lands on the newest entry.
        let stick = true;
        btn.addEventListener("click", () => toBottom(true));
        scroller.addEventListener("scroll", () => { stick = nearBottom(); sync(); }, { passive: true });

        const mo = new MutationObserver(() => {
            if (!scroller.isConnected) { mo.disconnect(); return; }
            if (stick) toBottom(false);
            sync();
        });
        mo.observe(scroller, { childList: true, subtree: true, characterData: true });

        toBottom(false);
        sync();
    });
}

/** The marked element itself when it scrolls, else its first scrollable descendant. */
function findScroller(el: HTMLElement): HTMLElement | null {
    if (scrolls(el)) return el;
    for (const child of Array.from(el.querySelectorAll<HTMLElement>("ul, div"))) {
        if (scrolls(child)) return child;
    }
    return null;
}

function scrolls(el: HTMLElement): boolean {
    const oy = getComputedStyle(el).overflowY;
    return oy === "auto" || oy === "scroll";
}
