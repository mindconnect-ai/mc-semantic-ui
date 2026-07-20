/*
 * Keep the app fully offline.
 *
 * The core SuiRenderer lazily loads Idiomorph from a CDN to preserve focus /
 * scroll / animation state across DOM swaps, falling back to innerHTML when the
 * network is unavailable. This app is meant to run with no external requests at
 * all (opened from a file server, a downloaded static site, etc.), so we pin the
 * plain innerHTML morpher up front. Full re-renders are the norm here (the tree
 * and preview remount wholesale), so there is nothing to gain from Idiomorph and
 * everything to gain from never touching the network.
 */
export function pinPlainMorpher(renderer) {
    if (typeof renderer.setMorpher === "function") {
        renderer.setMorpher((target, html, mode) => {
            if (mode === "outerHTML") target.outerHTML = html;
            else target.innerHTML = html;
        });
    }
    return renderer;
}
